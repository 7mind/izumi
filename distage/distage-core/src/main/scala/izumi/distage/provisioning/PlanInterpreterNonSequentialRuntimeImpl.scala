package izumi.distage.provisioning

import distage.{Id, SafeType}
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, IntegrationCheckException, UnexpectedDIException}
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{DIPlan, ExecutableOp}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.reflect.TagK

import java.util.concurrent.TimeUnit
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

  private[this] def instantiateImpl[F[_]: TagK](
    diplan: DIPlan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val ctx = ProvisionMutable[F](diplan, parentContext)

    def run(state: TraversalState): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
      state.current() match {
        case TraversalState.Step(steps) =>
          val ops = prioritize(steps.toSeq.map(k => diplan.plan.meta.nodes(k)))

          for {
            results <- F.traverse(ops)(op => process(ctx, op))
            out <- F.traverse(results) {
              case s: TimedResult.Success =>
                addResult(ctx, s)
              case f: TimedResult.Failure =>
                F.pure(f.toFinal: TimedFinalResult)
            }
            (ok, bad) = out.partition(_.isSuccess)
            out <- run(state.next(ok.asInstanceOf[List[TimedFinalResult.Success]], bad.asInstanceOf[List[TimedFinalResult.Failure]]))
          } yield {
            out
          }
        case TraversalState.Done() =>
          if (state.failures.isEmpty) {
            F.maybeSuspend(Right(ctx.finish(state)))
          } else {
            F.pure(Left(ctx.makeFailure(state, fullStackTraces)))
          }
        case TraversalState.CannotProgress(_) =>
          F.pure(Left(ctx.makeFailure(state, fullStackTraces)))
      }
    }

    for {
      result <- verifyEffectType(diplan.plan.meta.nodes.values)
      initial = TraversalState(
        diplan.plan.predecessors
      )
      out <- result match {
        case Left(value) =>
          val failures = value.map {
            e =>
              TimedFinalResult.Failure(
                e.key,
                List(e),
                FiniteDuration(0, TimeUnit.SECONDS),
              )
          }.toList
          val failed = initial.next(List.empty, failures)
          F.pure(Left(ctx.makeFailure(failed, fullStackTraces)))
        case Right(_) =>
          run(initial)
      }

    } yield {
      out
    }

  }

  private[this] def prioritize(value: Seq[ExecutableOp]): Seq[ExecutableOp] = {
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

  private def process[F[_]: TagK](context: ProvisionMutable[F], op: ExecutableOp)(implicit F: QuasiIO[F]): F[TimedResult] = {
    for {
      before <- F.maybeSuspend(System.nanoTime())
      out <- op match {
        case op: ImportDependency =>
          F.pure(importStrategy.importDependency(context.asContext(), op))
        case op: NonImportOp =>
          operationExecutor.execute(context.asContext(), op)
      }
      after <- F.maybeSuspend(System.nanoTime())
    } yield {
      val duration = Duration.fromNanos(after - before)
      out match {
        case Left(value) =>
          TimedResult.Failure(op.target, value, duration)
        case Right(value) =>
          TimedResult.Success(op.target, value, duration)
      }
    }
  }

  private[this] def addResult[F[_]: TagK](active: ProvisionMutable[F], result: TimedResult.Success)(implicit F: QuasiIO[F]): F[TimedFinalResult] = {
    for {
      out <- F.traverse(result.ops) {
        op =>
          for {
            _ <- op match {
              case i: NewObjectOp.LocalInstance =>
                System.err.println(s"check: ${i.implKey} <:< ${SafeType.get[IntegrationCheck[Identity]]} == ${i.implKey <:< SafeType.get[IntegrationCheck[Identity]]}")
                System.err.println(s"check: ${i.implKey} <:< ${SafeType.get[IntegrationCheck[F]]} == ${i.implKey <:< SafeType.get[IntegrationCheck[F]]}")

                if (i.implKey <:< SafeType.get[IntegrationCheck[Identity]]) {
                  System.err.println(s"id: $i")

                  F.maybeSuspend {
                    Option(i.instance).map(i => checkOrThrow(i.asInstanceOf[IntegrationCheck[Identity]])) match {
                      case Some(_) =>
                        ()
                      case None =>
                        ()
                    }
                  }
                } else if (i.implKey <:< SafeType.get[IntegrationCheck[F]]) {
                  System.err.println(s"F: $i")
                  Option(i.instance.asInstanceOf[IntegrationCheck[F]]) match {
                    case Some(value) =>
                      checkOrThrow(value)
                    case None =>
                      F.unit
                  }
                } else {
                  F.unit
                }
              case _ =>
                F.unit
            }
            out <- F.maybeSuspend {
              Try(active.addResult(verifier, op)).toEither.left.map(t => UnexpectedDIException(result.key, t))
            }
          } yield {
            out
          }

      }
    } yield {
      import izumi.functional.IzEither.*
      val r = out.biAggregateScalar
      r match {
        case Left(value) =>
          TimedFinalResult.Failure(result.key, value, result.time)
        case Right(_) =>
          TimedFinalResult.Success(result.key, result.time)
      }
    }

  }

  private[this] def checkOrThrow[F[_]: TagK](check: IntegrationCheck[F])(implicit F: QuasiIO[F]): F[Unit] = {
    System.err.println(s"cor on $check => ${check.resourcesAvailable()}")

    F.map(check.resourcesAvailable()) {
      case ResourceCheck.Success() =>
        F.unit
      case failure: ResourceCheck.Failure =>
        F.fail(new IntegrationCheckException(NonEmptyList(failure)))
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
