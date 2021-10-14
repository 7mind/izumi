package izumi.distage.provisioning

import distage.{Id, SafeType}
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
import izumi.fundamentals.platform.strings.IzString.toRichIterable

import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

class PlanInterpreterNonSequentialRuntimeImpl(
  importStrategy: ImportStrategy,
  operationExecutor: OperationExecutor,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
) extends PlanInterpreter {

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

  def prioritize(value: Seq[ExecutableOp]): Seq[ExecutableOp] = {
    value.sortBy {
      op =>
        val repr = op.target.tpe.toString
        op match {
          case _: ImportDependency =>
            (-10, repr)
          case op: InstantiationOp if op.instanceType <:< SafeType.get[AbstractCheck] =>
            (0, repr)
          case _ =>
            (1, repr)
        }
    }
  }

  private[this] def instantiateImpl[F[_]: TagK](
    diplan: DIPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val ctx = ProvisionMutable[F](diplan, parentContext)

    def run(state: TraversalState): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
      import izumi.functional.IzEither.*

      // TODO: better metadata

      state.next(Set.empty) match {
        case TraversalState.Step(next, steps) =>
          val ops = prioritize(steps.toSeq.map(k => diplan.plan.meta.nodes(k)))
          val todo = ops.map {
            op =>
              process(ctx, op)
          }

          for {
            ctxops <- F.map(F.traverse(todo)(a => a))(_.biAggregateScalar.map(_.flatten))
            result <- ctxops match {
              case Right(ops) =>
                F.map(F.traverse(ops)(op => addResult(ctx, op)))(_.biAggregateScalar)
              case Left(issues) =>
                F.pure(Left(issues))
            }
            out <- result match {
              case Left(value) =>
                F.pure(
                  Left(
                    ctx.makeFailure(List(ProvisioningFailure.AggregateFailure(value)), fullStackTraces)
                  )
                )
              case Right(_) =>
                run(next)
            }
          } yield {
            out
          }
        case TraversalState.Done() =>
          F.maybeSuspend {
            Right(ctx.finish())
          }
        case TraversalState.CannotProgress(left) =>
          F.pure(
            Left(
              ctx.makeFailure(List(ProvisioningFailure.BrokenGraph(left)), fullStackTraces)
            )
          )
      }
    }

    for {
      result <- verifyEffectType(diplan.plan.meta.nodes.values)
      out <- result match {
        case Left(value) =>
          F.pure(Left(ctx.makeFailure(List(ProvisioningFailure.AggregateFailure(value.toList)), fullStackTraces)))
        case Right(_) =>
          run(TraversalState(diplan.plan.predecessors))
      }

    } yield {
      out
    }

  }

  private def timed[F[_]: TagK, T](op: => F[T])(implicit F: QuasiIO[F]): F[(T, FiniteDuration)] = {
    for {
      before <- F.maybeSuspend(System.nanoTime())
      out <- op
      after <- F.maybeSuspend(System.nanoTime())
    } yield {
      (out, Duration.fromNanos(after - before))
    }
  }

  private def process[F[_]: TagK](context: ProvisionMutable[F], op: ExecutableOp)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    for {
      out <- timed(op match {
        case op: ImportDependency =>
          F.pure(importStrategy.importDependency(context.asContext(), op))
        case op: NonImportOp =>
          operationExecutor.execute(context.asContext(), op)
      })
    } yield {
      context.setMetaTiming(op.target, out._2)
      out._1
    }

  }

  private[this] def addResult[F[_]: TagK](active: ProvisionMutable[F], result: NewObjectOp)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Unit]] = {
    F.maybeSuspend {
      val t = Try {
        active.addResult(verifier, result)
      }.toEither
      t.left.map(t => UnexpectedDIException(result.key, t))
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Iterable[ExecutableOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Iterable[IncompatibleEffectTypesException], Unit]] = {
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
