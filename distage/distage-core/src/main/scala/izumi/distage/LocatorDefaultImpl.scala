package izumi.distage

import izumi.distage.model.Locator
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.provisioning.{PlanInterpreter, Provision}
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.reflect.TagK

final class LocatorDefaultImpl[F[_]](
  val plan: OrderedPlan,
  val parent: Option[Locator],
  private val dependencyMap: Provision[F],
) extends AbstractLocator {
  override protected def lookupLocalUnsafe(key: DIKey): Option[Any] =
    dependencyMap.get(key)

  override def finalizers[F1[_]: TagK]: collection.Seq[PlanInterpreter.Finalizer[F1]] = {
    dependencyMap.finalizers
      .filter(_.fType == SafeType.getK[F1])
      .map(_.asInstanceOf[PlanInterpreter.Finalizer[F1]])
  }

  override def instances: collection.Seq[IdentifiedRef] =
    dependencyMap.enumerate
}
