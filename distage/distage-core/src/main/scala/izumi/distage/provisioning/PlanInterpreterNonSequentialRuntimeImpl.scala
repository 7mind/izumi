package izumi.distage.provisioning

import distage.{DIKey, Id, Roots, SafeType}
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.definition.errors.ProvisionerIssue.IncompatibleEffectTypes
import izumi.distage.model.definition.errors.ProvisionerIssue.ProvisionerExceptionIssue.{IntegrationCheckFailure, UnexpectedIntegrationCheck}
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.model.plan.ExecutableOp.*
import izumi.distage.model.plan.{ExecutableOp, Plan}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionInternal, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.{Locator, Planner}
import izumi.distage.provisioning.PlanInterpreterNonSequentialRuntimeImpl.{abstractCheckType, integrationCheckIdentityType, nullType}
import izumi.functional.quasi.QuasiIO
import izumi.functional.quasi.QuasiIO.syntax.*
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptySet}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.reflect.TagK

import java.util.concurrent.TimeUnit
import scala.annotation.nowarn
import scala.collection.compat.immutable.ArraySeq
import scala.concurrent.duration.{Duration, FiniteDuration}

class PlanInterpreterNonSequentialRuntimeImpl(
  planner: Planner,
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
  ): Lifecycle[F, Either[FailedProvision, Locator]] = {
    Lifecycle
      .make(
        acquire = instantiateImpl(plan, parentLocator)
      )(release = {
        resource =>
          val finalizers = resource match {
            case Left(failedProvision) => failedProvision.provision.finalizers
            case Right(locator) => locator.finalizers
          }
          filterFinalizers.filter(finalizers).foldLeft(F.unit) {
            case (acc, f) => acc.guarantee(F.suspendF(f.effect()))
          }
      }).map(_.left.map(_.fail))
  }

  private[this] def instantiateImpl[F[_]: TagK](
    plan: Plan,
    parentContext: Locator,
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvisionInternal[F], LocatorDefaultImpl[F]]] = {
    val integrationCheckFType = SafeType.get[IntegrationCheck[F]]

    val ctx: ProvisionMutable[F] = new ProvisionMutable[F](plan, parentContext)

    @nowarn("msg=Unused import")
    def run(state: TraversalState, integrationPaths: Set[DIKey]): F[Either[TraversalState, Either[FailedProvisionInternal[F], LocatorDefaultImpl[F]]]] = {
      import scala.collection.compat.*

      state.current match {
        case TraversalState.Current.Step(steps) =>
          val ops = prioritize(steps.map(plan.plan.meta.nodes(_)), integrationPaths)

          for {
            results <- F.traverse(ops)(processOp(ctx, _))
            timedResults <- F.traverse(results) {
              case s: TimedResult.Success =>
                addIntegrationCheckResult(ctx, integrationCheckFType, s)
              case f: TimedResult.Failure =>
                F.pure(f.toFinal: TimedFinalResult)
            }
            (ok, bad) = timedResults.partitionMap {
              case ok: TimedFinalResult.Success => Left(ok)
              case bad: TimedFinalResult.Failure => Right(bad)
            }
            nextState = state.next(ok, bad)
          } yield Left(nextState)

        case TraversalState.Current.Done() =>
          if (state.failures.isEmpty) {
            F.maybeSuspend(Right(Right(ctx.finish(state))))
          } else {
            F.pure(Right(Left(ctx.makeFailure(state, fullStackTraces))))
          }
        case TraversalState.Current.CannotProgress(_) =>
          F.pure(Right(Left(ctx.makeFailure(state, fullStackTraces))))
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
            case Right(icPlan) =>
              F.tailRecM(initial)(run(_, icPlan.plan.meta.nodes.keySet))
          }
      }
    } yield res
  }

  private def failEarly[F[_]: TagK, A](
    ctx: ProvisionMutable[F],
    initial: TraversalState,
    issues: Iterable[ProvisionerIssue],
  )(implicit F: QuasiIO[F]
  ): F[Either[FailedProvisionInternal[F], A]] = {
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
  ): F[Either[FailedProvisionInternal[F], Plan]] = {
    val allChecks = ctx.plan.stepsUnordered.iterator.collect {
      case op: InstantiationOp if op.instanceType <:< abstractCheckType => op
    }.toSet
    if (allChecks.nonEmpty) {
      NonEmptySet.from(allChecks.map(_.target)) match {
        case Some(integrationChecks) =>
          F.maybeSuspend {
            planner
              .plan(ctx.plan.input.copy(roots = Roots.Of(integrationChecks)))
              .left.map(errs => ctx.makeFailure(state, fullStackTraces, ProvisioningFailure.CantBuildIntegrationSubplan(errs, state.status())))
          }
        case None =>
          F.pure(Right(Plan.empty))
      }
    } else {
      F.pure(Right(Plan.empty))
    }
  }

  private[this] def prioritize(ops: Iterable[ExecutableOp], integrationPaths: Set[DIKey]): Seq[ExecutableOp] = ArraySeq.unsafeWrapArray {
    ops.toArray.sortBy {
      op =>
        val repr = op.target.tpe.tag.repr
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
          F.pure(importStrategy.importDependency(context.asContext(), context.plan, op))
        case _: AddRecursiveLocatorRef =>
          F.pure(Right(context.locatorInstance()))
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

  private[this] def addIntegrationCheckResult[F[_]: TagK](
    active: ProvisionMutable[F],
    integrationCheckFType: SafeType,
    result: TimedResult.Success,
  )(implicit F: QuasiIO[F]
  ): F[TimedFinalResult] = {
    for {
      res <- F.traverse(result.ops) {
        op =>
          F.definitelyRecoverWithTrace[Option[ProvisionerIssue]](
            runIfIntegrationCheck(op, integrationCheckFType).flatMap {
              case None =>
                F.maybeSuspend {
                  active.addResult(verifier, op)
                  None
                }
              case failure @ Some(_) =>
                F.pure(failure)
            }
          )((_, trace) => F.pure(Some(UnexpectedIntegrationCheck(result.key, trace.unsafeAttachTraceOrReturnNewThrowable()))))
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

  private[this] def runIfIntegrationCheck[F[_]: TagK](op: NewObjectOp, integrationCheckFType: SafeType)(implicit F: QuasiIO[F]): F[Option[IntegrationCheckFailure]] = {
    op match {
      case i: NewObjectOp.CurrentContextInstance =>
        if (i.implType <:< nullType) {
          F.pure(None)
        } else if (i.implType <:< integrationCheckIdentityType) {
          F.maybeSuspend {
            checkOrFail[Identity](i.key, i.instance)
          }
        } else if (i.implType <:< integrationCheckFType) {
          checkOrFail[F](i.key, i.instance)
        } else {
          F.pure(None)
        }
      case _ =>
        F.pure(None)
    }
  }

  private[this] def checkOrFail[F[_]: TagK](key: DIKey, resource: Any)(implicit F: QuasiIO[F]): F[Option[IntegrationCheckFailure]] = {
    F.suspendF {
      resource
        .asInstanceOf[IntegrationCheck[F]]
        .resourcesAvailable()
        .flatMap {
          case ResourceCheck.Success() =>
            F.pure(None)
          case failure: ResourceCheck.Failure =>
            F.pure(Some(IntegrationCheckFailure(key, new IntegrationCheckException(NonEmptyList(failure)))))
        }
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](
    ops: Iterable[ExecutableOp]
  )(implicit F: QuasiIO[F]
  ): F[Either[Iterable[IncompatibleEffectTypes], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleEffectType[F])
      .map(op => IncompatibleEffectTypes(op, op.provisionerEffectType[F], op.actionEffectType))

    if (badOps.isEmpty) {
      F.pure(Right(()))
    } else {
      F.pure(Left(badOps))
    }
  }

}

private object PlanInterpreterNonSequentialRuntimeImpl {
  private val abstractCheckType: SafeType = SafeType.get[AbstractCheck]
  private val integrationCheckIdentityType: SafeType = SafeType.get[IntegrationCheck[Identity]]
  private val nullType: SafeType = SafeType.get[Null]
}
