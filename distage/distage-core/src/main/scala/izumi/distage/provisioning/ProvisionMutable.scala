package izumi.distage.provisioning

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.provisioning.{NewObjectOp, Provision, ProvisioningFailure}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey
import izumi.reflect.TagK

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

final case class ProvisionMutable[F[_]: TagK](diplan: Plan, parentContext: Locator) extends Provision[F] {

  private val temporaryLocator = new LocatorDefaultImpl(diplan, Option(parentContext), LocatorMeta.empty, this)

  private val locatorRef = new LocatorRef(new AtomicReference(Left(temporaryLocator)))
  type OperationMetadata = FiniteDuration

  def makeFailure(state: TraversalState, fullStackTraces: Boolean): FailedProvision[F] = {
    val diag = if (state.failures.isEmpty) {
      ProvisioningFailure.BrokenGraph(state.preds, state.status())
    } else {
      ProvisioningFailure.AggregateFailure(state.preds, state.failures, state.status())
    }
    makeFailure(state, fullStackTraces, diag)
  }

  def makeFailure(state: TraversalState, fullStackTraces: Boolean, diag: ProvisioningFailure): FailedProvision[F] = {
    val meta = FailedProvisionMeta(state.status())

    FailedProvision(
      toImmutable,
      diplan,
      parentContext,
      diag,
      meta,
      fullStackTraces,
    )
  }

  def finish(state: TraversalState): LocatorDefaultImpl[F] = {
    val meta = LocatorMeta(state.status())
    val finalLocator =
      new LocatorDefaultImpl(diplan, Option(parentContext), meta, toImmutable)
    locatorRef.ref.set(Right(finalLocator))
    finalLocator
  }

  override val instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any](
    DIKey.get[LocatorRef] -> locatorRef
  )
  override val imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
  override val finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]]()

  def toImmutable: ProvisionImmutable[F] = {
    ProvisionImmutable(instances, imports, finalizers)
  }

  def asContext(): LocatorContext = {
    LocatorContext(toImmutable, parentContext)
  }

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F] = {
    toImmutable.narrow(allRequiredKeys)
  }

  def addResult(verifier: ProvisionOperationVerifier, result: NewObjectOp): Unit = {
    result match {
      case NewObjectOp.NewImport(target, instance) =>
        verifier.verify(target, this.imports.keySet, instance, s"import")
        this.imports += (target -> instance)

      case NewObjectOp.NewInstance(target, _, instance) =>
        verifier.verify(target, this.instances.keySet, instance, "instance")
        this.instances += (target -> instance)

      case NewObjectOp.UseInstance(target, instance) =>
        verifier.verify(target, this.instances.keySet, instance, "reference")
        this.instances += (target -> instance)

      case r @ NewObjectOp.NewResource(target, _, instance, _) =>
        verifier.verify(target, this.instances.keySet, instance, "resource")
        this.instances += (target -> instance)
        val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
        this.finalizers prepend Finalizer[F](target, finalizer)

      case r @ NewObjectOp.NewFinalizer(target, _) =>
        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
        this.finalizers prepend Finalizer[F](target, finalizer)

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, this.instances.keySet, instance, "set")
        this.instances += (target -> instance)
    }
  }
}
