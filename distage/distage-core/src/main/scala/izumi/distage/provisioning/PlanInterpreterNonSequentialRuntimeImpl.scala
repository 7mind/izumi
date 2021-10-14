package izumi.distage.provisioning

import distage.Id
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, MissingImport, ProvisionerIssue, UnexpectedDIException}
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{DIPlan, ExecutableOp}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.reflect.TagK
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.reflection.DIKey

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.Try

class PlanInterpreterNonSequentialRuntimeImpl(
  importStrategy: ImportStrategy,
  operationExecutor: OperationExecutor,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
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

  private[this] def instantiateImpl[F[_]: TagK](
    diplan: DIPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F](diplan, parentContext)
    val meta = mutable.HashMap.empty[DIKey, OperationMetadata]

    def run(state: TraversalState): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
      import izumi.functional.IzEither.*
      state.next(Set.empty) match {
        case TraversalState.Step(next, steps) =>
          val ops = steps.toSeq.map(k => diplan.plan.meta.nodes(k))
          val todo = ops.map {
            op =>
              process(mutProvisioningContext.asContext(), op)
          }

          for {
            ctxops <- F.map(F.traverse(todo)(a => a))(_.biAggregateScalar.map(_.flatten))
            result <- ctxops match {
              case Right(ops) =>
                F.map(F.traverse(ops)(op => interpretResult(mutProvisioningContext, op)))(_.biAggregateScalar)
              case Left(issues) =>
                F.pure(Left(issues))
            }
            out <- result match {
              case Left(value) =>
                F.pure(
                  Left(
                    FailedProvision(
                      mutProvisioningContext.toImmutable,
                      diplan,
                      parentContext,
                      List(AggregateFailure(value)),
                      FailedProvisionMeta(LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap).timings),
                      fullStackTraces,
                    )
                  )
                )
              case Right(value) =>
                run(next)
            }
          } yield {
            out
          }
        case TraversalState.Done() =>
          F.maybeSuspend {
            val finalLocator =
              new LocatorDefaultImpl(diplan, Option(parentContext), LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap), mutProvisioningContext.toImmutable)
            mutProvisioningContext.locatorRef.ref.set(Right(finalLocator))
            Right(finalLocator)
          }
        case TraversalState.Problem(left) =>
          System.err.println(left)
          F.pure(
            Left(
              FailedProvision(
                mutProvisioningContext.toImmutable,
                diplan,
                parentContext,
                List(AggregateFailure(List.empty)),
                FailedProvisionMeta(LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap).timings),
                fullStackTraces,
              )
            )
          )
      }
    }
    run(TraversalState(diplan.plan.predecessors))
  }

  private def process[F[_]: TagK](context: ProvisioningKeyProvider, op: ExecutableOp)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    op match {
      case op: ImportDependency =>
        F.pure(importStrategy.importDependency(context, op))
      case op: NonImportOp =>
        operationExecutor.execute(context, op)
    }
  }

  private[this] def interpretResult[F[_]: TagK](active: ProvisionMutable[F], result: NewObjectOp)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Unit]] = {
    F.maybeSuspend {
      val t = Try {
        result match {
          case NewObjectOp.NewImport(target, instance) =>
            verifier.verify(target, active.imports.keySet, instance, s"import")
            active.imports += (target -> instance)
            ()

          case NewObjectOp.NewInstance(target, instance) =>
            verifier.verify(target, active.instances.keySet, instance, "instance")
            active.instances += (target -> instance)
            ()

          case r @ NewObjectOp.NewResource(target, instance, _) =>
            verifier.verify(target, active.instances.keySet, instance, "resource")
            active.instances += (target -> instance)
            val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
            active.finalizers prepend Finalizer[F](target, finalizer)
            ()

          case r @ NewObjectOp.NewFinalizer(target, _) =>
            val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
            active.finalizers prepend Finalizer[F](target, finalizer)
            ()

          case NewObjectOp.UpdatedSet(target, instance) =>
            verifier.verify(target, active.instances.keySet, instance, "set")
            active.instances += (target -> instance)
            ()
        }
      }.toEither

      t.left.map(t => UnexpectedDIException(result.key, t))
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
