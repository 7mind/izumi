package izumi.distage.provisioning

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.plan.DIPlan
import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.provisioning.Provision
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

final case class ProvisionMutable[F[_]](diplan: DIPlan, parentContext: Locator) extends Provision[F] {
  private val temporaryLocator = new LocatorDefaultImpl(diplan, Option(parentContext), LocatorMeta.empty, this)
  val locatorRef = new LocatorRef(new AtomicReference(Left(temporaryLocator)))
  override val instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any](
    DIKey.get[LocatorRef] -> locatorRef
  )
  override val imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
  override val finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]]()

  def toImmutable: ProvisionImmutable[F] = {
    ProvisionImmutable(instances, imports, finalizers)
  }

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F] = {
    toImmutable.narrow(allRequiredKeys)
  }
}
