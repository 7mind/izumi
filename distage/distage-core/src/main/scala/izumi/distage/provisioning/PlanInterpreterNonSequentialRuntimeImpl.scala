package izumi.distage.provisioning

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.IncompatibleEffectTypesException
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{DIPlan, ExecutableOp}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.reflect.TagK

class PlanInterpreterNonSequentialRuntimeImpl(
  importStrategy: ImportStrategy,
  operationExecutor: OperationExecutor,
  verifier: ProvisionOperationVerifier,
) extends PlanInterpreter {

  type OperationMetadata = Long

  override def run[F[_]: TagK: QuasiIO](
    plan: DIPlan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
    val F: QuasiIO[F] = implicitly[QuasiIO[F]]
    Lifecycle.make(
      acquire = instantiateImpl(plan, parentLocator)
    )(release = {
      resource =>
        val finalizers = resource match {
          case Left(failedProvision) => failedProvision.failed.finalizers
          case Right(locator) => locator.finalizers
        }
        filterFinalizers.filter(finalizers).foldLeft(F.unit) {
          case (acc, f) => acc.guarantee(F.suspendF(f.effect()))
        }
    })
  }

  private def process[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, op: ExecutableOp) = {
    op match {
      case op: ImportDependency =>
        importStrategy.importDependency(context, op)
      case op: NonImportOp =>
        operationExecutor.execute(context, op)
    }
  }

  private[this] def instantiateImpl[F[_]: TagK](
    diplan: DIPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    ???
//    val mutProvisioningContext = Prov.make(diplan, parentContext)
//
//    def run(state: TraversalState) = {
//      state.next() match {
//        case TraversalState.Step(next, steps) =>
//          val ops = steps.toSeq.map(k => diplan.plan.meta.nodes(k))
//          ops.map {}
//        case TraversalState.Done() =>
//        case TraversalState.Problem(left) =>
//      }
//    }
//    run(TraversalState(diplan.plan.predecessors.links))

  }

  private[this] def interpretResult[F[_]: TagK](active: ProvisionMutable[F], result: NewObjectOp): Unit = {
    result match {
      case NewObjectOp.NewImport(target, instance) =>
        verifier.verify(target, active.imports.keySet, instance, s"import")
        active.imports += (target -> instance)

      case NewObjectOp.NewInstance(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "instance")
        active.instances += (target -> instance)

      case r @ NewObjectOp.NewResource(target, instance, _) =>
        verifier.verify(target, active.instances.keySet, instance, "resource")
        active.instances += (target -> instance)
        val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case r @ NewObjectOp.NewFinalizer(target, _) =>
        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Seq[NonImportOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Seq[IncompatibleEffectTypesException], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleEffectType[F])
      .map {
        op =>
          IncompatibleEffectTypesException(op, op.provisionerEffectType[F], op.actionEffectType)
      }

    F.ifThenElse(badOps.isEmpty)(F.pure(Right(())), F.pure(Left(badOps)))
  }

}
