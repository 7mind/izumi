package izumi.distage.provisioning

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionInternal, FailedProvisionMeta, Finalizer}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.provisioning.{NewObjectOp, Provision, ProvisioningFailure}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey
import izumi.reflect.TagK

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

final class ProvisionMutable[F[_]: TagK](
  val plan: Plan,
  parentContext: Locator,
  privateBindings: Set[DIKey],
) extends Provision[F] {

  private val temporaryLocator = new LocatorDefaultImpl(plan, Option(parentContext), LocatorMeta.empty, this, privateBindings)

  private val locatorRef = new LocatorRef(new AtomicReference(Left(temporaryLocator)))

  def locatorInstance(): Seq[NewObjectOp] = {
    Seq(NewObjectOp.NewImport(DIKey.get[LocatorRef], locatorRef))
  }

  def makeFailure(state: TraversalState, fullStackTraces: Boolean): FailedProvisionInternal[F] = {
    val diag = if (state.failures.isEmpty) {
      ProvisioningFailure.BrokenGraph(state.preds, state.status())
    } else {
      ProvisioningFailure.AggregateFailure(state.preds, state.failures, state.status())
    }
    makeFailure(state, fullStackTraces, diag)
  }

  def makeFailure(state: TraversalState, fullStackTraces: Boolean, diag: ProvisioningFailure): FailedProvisionInternal[F] = {
    val meta = FailedProvisionMeta(state.status())

    val prov = toImmutable
    FailedProvisionInternal(
      prov,
      FailedProvision(
        failed = toImmutable.raw,
        plan = plan,
        parentContext = parentContext,
        failure = diag,
        meta = meta,
        fullStackTraces = fullStackTraces,
      ),
    )
  }

  def finish(state: TraversalState): LocatorDefaultImpl[F] = {
    val meta = LocatorMeta(state.status())
    val finalLocator =
      new LocatorDefaultImpl(plan, Option(parentContext), meta, toImmutable, privateBindings)
    locatorRef.ref.set(Right(finalLocator))
    finalLocator
  }

  override val instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap.empty[DIKey, Any]
  override val imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
  override val finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]]()

  def toImmutable: ProvisionImmutable[F] = {
    ProvisionImmutable(instances, imports, finalizers)
  }

  def asContext(): LocatorContext = {
    LocatorContext(toImmutable, parentContext, plan)
  }

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F] = {
    toImmutable.narrow(allRequiredKeys)
  }

  def addResult(verifier: ProvisionOperationVerifier, result: NewObjectOp): Option[ProvisionerIssue] = {
    (result match {
      case NewObjectOp.NewImport(target, instance) =>
        verifier.verify(target, this.imports.keySet, instance, s"import").map {
          _ =>
            this.imports += (target -> instance)
            ()
        }

      case NewObjectOp.NewInstance(target, _, instance) =>
        verifier.verify(target, this.instances.keySet, instance, "instance").map {
          _ =>
            this.instances += (target -> instance)
            ()
        }

      case NewObjectOp.UseInstance(target, instance) =>
        verifier.verify(target, this.instances.keySet, instance, "reference").map {
          _ =>
            this.instances += (target -> instance)
            ()
        }

      case r @ NewObjectOp.NewResource(target, _, instance, _) =>
        verifier.verify(target, this.instances.keySet, instance, "resource").map {
          _ =>
            this.instances += (target -> instance)
            val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
            this.finalizers prepend Finalizer[F](target, finalizer)
            ()
        }

      case r @ NewObjectOp.NewFinalizer(target, _) =>
        Right {
          val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
          this.finalizers prepend Finalizer[F](target, finalizer)
          ()
        }
    }).swap.toOption
  }
}
