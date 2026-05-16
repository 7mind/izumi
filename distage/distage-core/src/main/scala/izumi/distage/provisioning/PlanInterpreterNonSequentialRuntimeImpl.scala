package izumi.distage.provisioning

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.definition.{Binding, BindingTag, Id, Lifecycle, LocatorPrivacy}
import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.definition.errors.ProvisionerIssue.IncompatibleEffectTypes
import izumi.distage.model.definition.errors.ProvisionerIssue.ProvisionerExceptionIssue.{IntegrationCheckFailure, UnexpectedIntegrationCheck}
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.model.plan.ExecutableOp.*
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Plan, Roots}
import izumi.distage.model.provisioning.*
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionInternal, FinalizerFilter}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.model.{Locator, Planner}
import izumi.distage.provisioning.PlanInterpreterNonSequentialRuntimeImpl.{abstractCheckType, integrationCheckIdentityBifunctorizedType, nullType}
import izumi.functional.bio.{Bifunctorized, Exit, IO2}
import izumi.fundamentals.collections.nonempty.{NEList, NESet}
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.reflect.{TagK, TagKK}

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

  override def run[F[+_, +_]: TagKK](
    plan: Plan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  )(implicit F: IO2[F],
    tkFThrowable: TagK[F[Throwable, _]],
  ): Lifecycle[F, Throwable, Either[FailedProvision, Locator]] = {
    Lifecycle
      .make[F, Throwable, Either[FailedProvisionInternal[F], LocatorDefaultImpl[F]]](
        acquire = instantiateImpl(plan, parentLocator)
      )(release = {
        resource =>
          val finalizers = resource match {
            case Left(failedProvision) => failedProvision.provision.finalizers
            case Right(locator) => locator.finalizers[F]
          }
          filterFinalizers.filter(finalizers).foldLeft(F.unit) {
            case (acc, f) => F.guarantee(acc, F.suspendSafe(f.effect()))
          }
      }).map(_.left.map(_.fail))
  }

  private def instantiateImpl[F[+_, +_]: TagKK](
    plan: Plan,
    parentContext: Locator,
  )(implicit F: IO2[F],
    tkFThrowable: TagK[F[Throwable, _]],
  ): F[Throwable, Either[FailedProvisionInternal[F], LocatorDefaultImpl[F]]] = {
    // Integration-check matching: two paths supported.
    //   1. `IntegrationCheck[IdentityBifunctorized[Throwable, _]]` — synchronous Identity carrier
    //      bindings (e.g. tests on `SpecIdentity`). The check method returns a synchronous
    //      `MiniBIO` which is run via `Bifunctorized.debifunctorizeIdentity` on the calling
    //      thread. This mirrors the Identity special-case in
    //      `EffectStrategyDefaultImpl`/`ResourceStrategyDefaultImpl` (M5/9a-9b).
    //   2. `IntegrationCheck[F[Throwable, _]]` — bifunctor-F carrier bindings (e.g. tests on
    //      `Spec2[F]`). The check method returns `F[Throwable, ResourceCheck]` and is run via
    //      `F.flatMap` in the surrounding pipeline. The SafeType for the binding's static type
    //      is constructed at runtime from `TagK[F[Throwable, _]]` (added M5-fix5b).
    val integrationCheckFType: SafeType = SafeType.get[IntegrationCheck[F[Throwable, _]]]

    val privateBindings = computePrivateBindings(plan)

    val ctx: ProvisionMutable[F] = new ProvisionMutable[F](plan, parentContext, privateBindings)

    @nowarn("msg=[Uu]nused import")
    def run(state: TraversalState, integrationPaths: Set[DIKey]): F[Throwable, Either[TraversalState, Either[FailedProvisionInternal[F], LocatorDefaultImpl[F]]]] = {
      import scala.collection.compat.*

      state.current match {
        case TraversalState.Current.Step(steps) =>
          val ops = prioritize(steps.map(plan.plan.meta.nodes(_)), integrationPaths)

          F.flatMap(F.traverse(ops)(processOp(ctx, _))) { results =>
            F.map(F.traverse(results) {
              case s: TimedResult.Success =>
                addIntegrationCheckResult(ctx, integrationCheckFType, s)
              case f: TimedResult.Failure =>
                F.pure(f.toFinal: TimedFinalResult)
            }) { timedResults =>
              val (ok, bad) = timedResults.partitionMap {
                case ok: TimedFinalResult.Success => Left(ok)
                case bad: TimedFinalResult.Failure => Right(bad)
              }
              val nextState = state.next(ok, bad)
              Left(nextState)
            }
          }

        case TraversalState.Current.Done() =>
          if (state.failures.isEmpty) {
            F.syncThrowable(Right(Right(ctx.finish(state))))
          } else {
            F.pure(Right(Left(ctx.makeFailure(state, fullStackTraces))))
          }
        case TraversalState.Current.CannotProgress(_) =>
          F.pure(Right(Left(ctx.makeFailure(state, fullStackTraces))))
      }
    }

    F.flatMap(verifyEffectType[F](plan.plan.meta.nodes.values)) { result =>
      val initial = TraversalState(plan.plan.predecessors)
      F.flatMap(integrationPlan(initial, ctx)) { icPlan =>
        result match {
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
      }
    }
  }

  private def computePrivateBindings(plan: Plan): Set[DIKey] = {
    def isRoot(target: DIKey): Boolean = {
      plan.input.roots match {
        case Roots.Of(roots) =>
          roots.contains(target)
        case Roots.Everything =>
          true
      }
    }

    def isPrivateBinding(target: DIKey, binding: Binding): Boolean = {
      plan.input.locatorPrivacy match {
        case LocatorPrivacy.PublicByDefault =>
          binding.tags.contains(BindingTag.Confined)
        case LocatorPrivacy.PrivateByDefault =>
          !binding.tags.contains(BindingTag.Exposed)
        case LocatorPrivacy.PublicRoots =>
          !isRoot(target) && !binding.tags.contains(BindingTag.Exposed)
      }
    }

    def isPrivate(op: ExecutableOp): Boolean = {
      op.origin.value match {
        case OperationOrigin.UserBinding(binding) =>
          isPrivateBinding(op.target, binding)
        case OperationOrigin.SyntheticBinding(binding) =>
          isPrivateBinding(op.target, binding)
        case OperationOrigin.Unknown =>
          plan.input.locatorPrivacy match {
            case LocatorPrivacy.PublicByDefault =>
              false
            case LocatorPrivacy.PrivateByDefault =>
              true
            case LocatorPrivacy.PublicRoots =>
              isRoot(op.target)
          }
      }
    }

    plan.stepsUnordered
      .filter(isPrivate)
      .map(_.target).toSet
  }

  private def failEarly[F[+_, +_], A](
    ctx: ProvisionMutable[F],
    initial: TraversalState,
    issues: Iterable[ProvisionerIssue],
  )(implicit F: IO2[F]
  ): F[Throwable, Either[FailedProvisionInternal[F], A]] = {
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

  private def integrationPlan[F[+_, +_]](
    state: TraversalState,
    ctx: ProvisionMutable[F],
  )(implicit F: IO2[F]
  ): F[Throwable, Either[FailedProvisionInternal[F], Plan]] = {
    val allChecks = ctx.plan.stepsUnordered.iterator.collect {
      case op: InstantiationOp if op.instanceType <:< abstractCheckType => op
    }.toSet
    if (allChecks.nonEmpty) {
      NESet.from(allChecks.map(_.target)) match {
        case Some(integrationChecks) =>
          F.syncThrowable {
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

  private def prioritize(ops: Iterable[ExecutableOp], integrationPaths: Set[DIKey]): Seq[ExecutableOp] = ArraySeq.unsafeWrapArray {
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

  private def processOp[F[+_, +_]: TagKK](context: ProvisionMutable[F], op: ExecutableOp)(implicit F: IO2[F]): F[Throwable, TimedResult] = {
    F.flatMap(F.syncThrowable(System.nanoTime())) { before =>
      val res = op match {
        case op: ImportDependency =>
          F.pure(importStrategy.importDependency(context.asContext(), context.plan, op))
        case _: AddRecursiveLocatorRef =>
          F.pure(Right(context.locatorInstance()))
        case op: NonImportOp =>
          operationExecutor.execute[F](context.asContext(), op)
      }
      F.flatMap(res) { r =>
        F.map(F.syncThrowable(System.nanoTime())) { after =>
          val duration = Duration.fromNanos(after - before)
          r match {
            case Left(value) =>
              TimedResult.Failure(op.target, value, duration)
            case Right(value) =>
              TimedResult.Success(op.target, value, duration)
          }
        }
      }
    }
  }

  private def addIntegrationCheckResult[F[+_, +_]](
    active: ProvisionMutable[F],
    integrationCheckFType: SafeType,
    result: TimedResult.Success,
  )(implicit F: IO2[F]
  ): F[Throwable, TimedFinalResult] = {
    F.map(F.traverse(result.ops) { op =>
      F.sandboxCatchAll[Throwable, Option[ProvisionerIssue], Throwable](
        F.flatMap(runIfIntegrationCheck(op, integrationCheckFType)) {
          case None =>
            F.syncThrowable {
              active.addResult(verifier, op)
              None: Option[ProvisionerIssue]
            }
          case failure @ Some(_) =>
            F.pure(failure)
        }
      )(
        (failure: Exit.FailureUninterrupted[Throwable]) =>
          F.pure(Some(UnexpectedIntegrationCheck(result.key, failure.trace.unsafeAttachTraceOrReturnNewThrowable())))
      )
    }) { res =>
      res.flatten match {
        case Nil =>
          TimedFinalResult.Success(result.key, result.time)
        case exceptions =>
          TimedFinalResult.Failure(result.key, exceptions, result.time)
      }
    }
  }

  private def runIfIntegrationCheck[F[+_, +_]](op: NewObjectOp, integrationCheckFType: SafeType)(implicit F: IO2[F]): F[Throwable, Option[IntegrationCheckFailure]] = {
    op match {
      case i: NewObjectOp.CurrentContextInstance =>
        if (i.implType <:< nullType) {
          F.pure(None)
        } else if (i.implType <:< integrationCheckIdentityBifunctorizedType) {
          // Identity-bifunctor carrier (`IntegrationCheck[IdentityBifunctorized[Throwable, _]]`):
          // run the MiniBIO `resourcesAvailable()` synchronously on the calling thread.
          // Mirrors the Identity special-case in `EffectStrategyDefaultImpl`.
          F.syncThrowable {
            checkOrFailIdentityBifunctorized(i.key, i.instance)
          }
        } else if (i.implType <:< integrationCheckFType) {
          // Bifunctor-F carrier (`IntegrationCheck[F[Throwable, _]]`): the check returns
          // `F[Throwable, ResourceCheck]`. Run it through `F.flatMap` so failures and defects
          // are routed through the surrounding `sandboxCatchAll`.
          checkOrFailF[F](i.key, i.instance)
        } else {
          F.pure(None)
        }
      case _ =>
        F.pure(None)
    }
  }

  private def checkOrFailIdentityBifunctorized(key: DIKey, resource: Any): Option[IntegrationCheckFailure] = {
    val miniBIO = resource
      .asInstanceOf[IntegrationCheck[Bifunctorized.IdentityBifunctorized[Throwable, _]]]
      .resourcesAvailable()
      .asInstanceOf[Bifunctorized.IdentityBifunctorized[Throwable, ResourceCheck]]
    Bifunctorized.debifunctorizeIdentity[ResourceCheck](miniBIO) match {
      case ResourceCheck.Success() =>
        None
      case failure: ResourceCheck.Failure =>
        Some(IntegrationCheckFailure(key, new IntegrationCheckException(NEList(failure))))
    }
  }

  private def checkOrFailF[F[+_, +_]](key: DIKey, resource: Any)(implicit F: IO2[F]): F[Throwable, Option[IntegrationCheckFailure]] = {
    F.map(
      resource
        .asInstanceOf[IntegrationCheck[F[Throwable, _]]]
        .resourcesAvailable()
        .asInstanceOf[F[Throwable, ResourceCheck]]
    ) {
      case ResourceCheck.Success() =>
        None
      case failure: ResourceCheck.Failure =>
        Some(IntegrationCheckFailure(key, new IntegrationCheckException(NEList(failure))))
    }
  }

  private def verifyEffectType[F[+_, +_]: TagKK](
    ops: Iterable[ExecutableOp]
  )(implicit F: IO2[F]
  ): F[Throwable, Either[Iterable[IncompatibleEffectTypes], Unit]] = {
    val monadicOps = ops.collect { case m: MonadicOp => m }
    val badOps = monadicOps
      .filter(_.isIncompatibleBifunctorEffectType[F])
      .map(op => IncompatibleEffectTypes(op, SafeType.getKK[F], op.actionEffectType))

    if (badOps.isEmpty) {
      F.pure(Right(()))
    } else {
      F.pure(Left(badOps))
    }
  }

}

private object PlanInterpreterNonSequentialRuntimeImpl {
  private val abstractCheckType: SafeType = SafeType.get[AbstractCheck]
  /** SafeType for `IntegrationCheck[IdentityBifunctorized[Throwable, _]]` — the post-M5
    * shape of synchronous Identity-effect integration-check bindings. Pre-M5 this was
    * `IntegrationCheck[Identity]` (where `Identity[A] = A`); the bifunctor migration
    * routes Identity through the MiniBIO-backed `IdentityBifunctorized` carrier (see
    * `Bifunctorized.scala`). The legacy `IntegrationCheck[Identity]` SafeType no longer
    * matches any binding produced by the bifunctorized DSL family.
    *
    * Mirrored from the `MonadicOp.identityBifunctorizedEffectType`-based Identity
    * special-case in `EffectStrategyDefaultImpl`/`ResourceStrategyDefaultImpl`. */
  private val integrationCheckIdentityBifunctorizedType: SafeType =
    SafeType.get[IntegrationCheck[Bifunctorized.IdentityBifunctorized[Throwable, _]]]
  private val nullType: SafeType = SafeType.get[Null]
}
