package izumi.distage.provisioning

import distage.{DIKey, Id, Injector, Roots, SafeType}
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.IntegrationCheckException
import izumi.distage.model.exceptions.interpretation.{IncompatibleEffectTypesException, ProvisionerIssue, UnexpectedDIException}
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, *}
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.provisioning.PlanInterpreterNonSequentialRuntimeImpl.{abstractCheckType, integrationCheckIdentityType, nullType}
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptySet}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.reflect.TagK

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

class PlanInterpreterNonSequentialRuntimeImpl(
  importStrategy: ImportStrategy,
  operationExecutor: OperationExecutor,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
) extends PlanInterpreter {

  override def run[F[_]: TagK](
    plan: Plan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  )(implicit F: QuasiIO[F]
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
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
    plan: Plan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val integrationCheckFType = SafeType.get[IntegrationCheck[F]]

    val ctx: ProvisionMutable[F] = ProvisionMutable[F](plan, parentContext)

    def run(state: TraversalState, integrationPaths: Set[DIKey]): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
      state.current match {
        case TraversalState.Step(steps) =>
          val ops = prioritize(steps.toSeq.map(plan.plan.meta.nodes(_)), integrationPaths)

          for {
            results <- F.traverse(ops)(processOp(ctx, _))
            timedResults <- F.traverse(results) {
              case s: TimedResult.Success =>
                addResult(ctx, integrationCheckFType, s)
              case f: TimedResult.Failure =>
                F.pure(f.toFinal: TimedFinalResult)
            }
            (ok, bad) = timedResults.partition(_.isSuccess)
            res <- run(
              state.next(ok.asInstanceOf[List[TimedFinalResult.Success]], bad.asInstanceOf[List[TimedFinalResult.Failure]]),
              integrationPaths,
            )
          } yield res
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
      result <- verifyEffectType(plan.plan.meta.nodes.values)
      initial = TraversalState(plan.plan.predecessors)
      icPlan <- integrationPlan(initial, ctx)

      res <- result match {
        case Left(incompatibleEffectTypes) =>
          failEarly(ctx, initial, incompatibleEffectTypes)

        case Right(()) =>
          icPlan match {
            case Left(failedProvision) =>
              F.pure(Left(failedProvision))
            case Right(diPlan) =>
              run(initial, diPlan.plan.meta.nodes.keySet)
          }
      }
    } yield res
  }

  private def failEarly[F[_]: TagK](
    ctx: ProvisionMutable[F],
    initial: TraversalState,
    issues: Iterable[ProvisionerIssue],
  )(implicit F: QuasiIO[F]
  ): F[Left[FailedProvision[F], Nothing]] = {
    val failures = issues.map {
      issue =>
        TimedFinalResult.Failure(
          issue.key,
          List(issue),
          FiniteDuration(0, TimeUnit.SECONDS),
        )
    }.toList
    val failed = initial.next(List.empty, failures)
    F.pure(Left(ctx.makeFailure(failed, fullStackTraces)))
  }

  private[this] def integrationPlan[F[_]: TagK](
    state: TraversalState,
    ctx: ProvisionMutable[F],
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvision[F], Plan]] = {
    val allChecks = ctx.diplan.stepsUnordered.collect {
      case op: InstantiationOp if op.instanceType <:< abstractCheckType =>
        op
    }.toSet
    if (allChecks.nonEmpty) {
      val icPlanner = Injector()
      NonEmptySet.from(allChecks.map(_.target)) match {
        case Some(value) =>
          F.maybeSuspend {
            icPlanner.planSafe(ctx.diplan.input.copy(roots = Roots.Of(value))).left.map {
              errs =>
                ctx.makeFailure(state, fullStackTraces, ProvisioningFailure.CantBuildIntegrationSubplan(errs, state.status()))
            }
          }
        case None =>
          F.pure(Right(Plan.empty))
      }
    } else {
      F.pure(Right(Plan.empty))
    }

  }

  private[this] def prioritize(value: Seq[ExecutableOp], integrationPaths: Set[DIKey]): Seq[ExecutableOp] = {
    value.sortBy {
      op =>
        val repr = op.target.tpe.toString
        op match {
          case _: ImportDependency =>
            (-10, repr)
          case op: InstantiationOp if integrationPaths.contains(op.target) =>
            (0, repr)
          case _ =>
            (1, repr)
        }
    }
  }

  private def processOp[F[_]: TagK](context: ProvisionMutable[F], op: ExecutableOp)(implicit F: QuasiIO[F]): F[TimedResult] = {
    for {
      before <- F.maybeSuspend(System.nanoTime())
      res <- op match {
        case op: ImportDependency =>
          F.pure(importStrategy.importDependency(context.asContext(), op))
        case op: NonImportOp =>
          operationExecutor.execute(context.asContext(), op)
      }
      after <- F.maybeSuspend(System.nanoTime())
    } yield {
      val duration = Duration.fromNanos(after - before)
      res match {
        case Left(value) =>
          TimedResult.Failure(op.target, value, duration)
        case Right(value) =>
          TimedResult.Success(op.target, value, duration)
      }
    }
  }

  private[this] def addResult[F[_]: TagK](
    active: ProvisionMutable[F],
    integrationCheckFType: SafeType,
    result: TimedResult.Success,
  )(implicit F: QuasiIO[F]
  ): F[TimedFinalResult] = {
    for {
      res <- F.traverse(result.ops) {
        op =>
          F.definitelyRecover[Option[UnexpectedDIException]](for {
            _ <- runIfIntegrationCheck(op, integrationCheckFType)
            _ <- F.maybeSuspend(active.addResult(verifier, op))
          } yield None)(exception => F.pure(Some(UnexpectedDIException(result.key, exception))))
      }
    } yield {
      res.flatten match {
        case Nil =>
          TimedFinalResult.Success(result.key, result.time)
        case exceptions =>
          TimedFinalResult.Failure(result.key, exceptions, result.time)
      }
    }
  }

  private[this] def runIfIntegrationCheck[F[_]: TagK](op: NewObjectOp, integrationCheckFType: SafeType)(implicit F: QuasiIO[F]): F[Unit] = {
    op match {
      case i: NewObjectOp.LocalInstance =>
        if (i.implType <:< nullType) {
          F.unit
        } else if (i.implType <:< integrationCheckIdentityType) {
          F.maybeSuspend {
            checkOrFail[Identity](i.instance)
          }
        } else if (i.implType <:< integrationCheckFType) {
          checkOrFail[F](i.instance)
        } else {
          F.unit
        }
      case _ =>
        F.unit
    }
  }

  private[this] def checkOrFail[F[_]: TagK](resource: Any)(implicit F: QuasiIO[F]): F[Unit] = {
    F.suspendF {
      resource
        .asInstanceOf[IntegrationCheck[F]].resourcesAvailable()
        .flatMap {
          case ResourceCheck.Success() =>
            F.unit
          case failure: ResourceCheck.Failure =>
            F.fail(new IntegrationCheckException(NonEmptyList(failure)))
        }
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Iterable[ExecutableOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Iterable[IncompatibleEffectTypesException], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleEffectType[F])
      .map(op => IncompatibleEffectTypesException(op, op.provisionerEffectType[F], op.actionEffectType))

    F.ifThenElse(badOps.isEmpty)(F.pure(Right(())), F.pure(Left(badOps)))
  }

}

private object PlanInterpreterNonSequentialRuntimeImpl {
  private val abstractCheckType: SafeType = SafeType.get[AbstractCheck]
  private val integrationCheckIdentityType: SafeType = SafeType.get[IntegrationCheck[Identity]]
  private val nullType: SafeType = SafeType.get[Null]
}
