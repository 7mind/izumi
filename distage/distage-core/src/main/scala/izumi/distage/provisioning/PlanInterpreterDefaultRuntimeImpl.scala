package izumi.distage.provisioning

import java.util.concurrent.atomic.AtomicReference

import distage.Id
import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.Locator.LocatorMeta
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiEffect
import izumi.distage.model.effect.QuasiEffect.syntax._
import izumi.distage.model.exceptions.IncompatibleEffectTypesException
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionMutable
import izumi.distage.model.provisioning._
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection._
import izumi.reflect.TagK

import scala.annotation.nowarn
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

// TODO: add introspection capabilities
class PlanInterpreterDefaultRuntimeImpl(
  setStrategy: SetStrategy,
  proxyStrategy: ProxyStrategy,
  providerStrategy: ProviderStrategy,
  importStrategy: ImportStrategy,
  instanceStrategy: InstanceStrategy,
  effectStrategy: EffectStrategy,
  resourceStrategy: ResourceStrategy,
  failureHandler: ProvisioningFailureInterceptor,
  verifier: ProvisionOperationVerifier,
  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
) extends PlanInterpreter
  with OperationExecutor {

  type OperationMetadata = Long

  override def instantiate[F[_]: TagK](
    plan: OrderedPlan,
    parentContext: Locator,
    filterFinalizers: FinalizerFilter[F],
  )(implicit F: QuasiEffect[F]
  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
    Lifecycle.make(
      acquire = instantiateImpl(plan, parentContext)
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
    plan: OrderedPlan,
    parentContext: Locator,
  )(implicit F: QuasiEffect[F]
  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F]()
    val temporaryLocator = new LocatorDefaultImpl(plan, Option(parentContext), LocatorMeta.empty, mutProvisioningContext)
    val locatorRef = new LocatorRef(new AtomicReference(Left(temporaryLocator)))
    mutProvisioningContext.instances.put(DIKey.get[LocatorRef], locatorRef)

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]
    val meta = mutable.HashMap.empty[DIKey, OperationMetadata]

    def processStep(step: ExecutableOp): F[Unit] = {
      for {
        before <- F.maybeSuspend(System.nanoTime())
        excludeOp <- F.maybeSuspend(mutExcluded.contains(step.target))
        _ <-
          if (excludeOp) {
            F.unit
          } else {
            val failureContext = ProvisioningFailureContext(parentContext, mutProvisioningContext, step)

            F.definitelyRecover[Try[Seq[NewObjectOp]]](
              action = execute(LocatorContext(mutProvisioningContext.toImmutable, parentContext), step).map(Success(_))
            )(recover =
              exception =>
                F.maybeSuspend {
                  failureHandler
                    .onExecutionFailed(failureContext)
                    .applyOrElse(exception, Failure(_: Throwable))
                }
            ).flatMap {
                case Success(newObjectOps) =>
                  F.maybeSuspend {
                    newObjectOps.foreach {
                      newObject =>
                        val maybeSuccess = Try {
                          interpretResult(mutProvisioningContext, newObject)
                        }.recoverWith(failureHandler.onBadResult(failureContext))

                        maybeSuccess match {
                          case Success(_) =>
                          case Failure(failure) =>
                            mutExcluded ++= plan.topology.transitiveDependees(step.target)
                            mutFailures += ProvisioningFailure(step, failure)
                        }
                    }
                  }

                case Failure(failure) =>
                  F.maybeSuspend {
                    mutExcluded ++= plan.topology.transitiveDependees(step.target)
                    mutFailures += ProvisioningFailure(step, failure)
                    ()
                  }
              }
          }
        after <- F.maybeSuspend(System.nanoTime())
      } yield {
        val time = after - before
        meta.put(step.target, time)
        ()
      }
    }

    val (imports, otherSteps) = plan.steps.partition {
      case _: ImportDependency => true
      case _ => false
    }

    @nowarn("msg=Unused import")
    def makeMeta(): LocatorMeta = {
      import scala.collection.compat._
      LocatorMeta(meta.view.mapValues(Duration.fromNanos).toMap)
    }

    def doFail(immutable: Provision.ProvisionImmutable[F]): Either[FailedProvision[F], LocatorDefaultImpl[F]] = {
      Left(FailedProvision[F](immutable, plan, parentContext, mutFailures.toVector, FailedProvisionMeta(makeMeta().timings), fullStackTraces))
    }

    for {
      // do imports first before everything
      _ <- F.traverse_(imports)(processStep)
      // verify effect type for everything else first before everything
      _ <- verifyEffectType[F](otherSteps)(failure => F.maybeSuspend(mutFailures += failure))

      failedImportsOrEffects <- F.maybeSuspend(mutFailures.nonEmpty)
      immutable = mutProvisioningContext.toImmutable
      res <-
        if (failedImportsOrEffects) {
          F.maybeSuspend(doFail(immutable))
        } else {
          F.traverse_(otherSteps)(processStep)
            .flatMap {
              _ =>
                F.maybeSuspend {
                  if (mutFailures.nonEmpty) {
                    doFail(immutable)
                  } else {
                    val finalLocator = new LocatorDefaultImpl(plan, Option(parentContext), makeMeta(), immutable)
                    val res = Right(finalLocator)
                    locatorRef.ref.set(res)
                    res
                  }
                }
            }
        }
    } yield res
  }

  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: ExecutableOp)(implicit F: QuasiEffect[F]): F[Seq[NewObjectOp]] = {
    step match {
      case op: ImportDependency =>
        F pure importStrategy.importDependency(context, this, op)

      case op: CreateSet =>
        F pure setStrategy.makeSet(context, this, op)

      case op: WiringOp =>
        F pure execute(context, op)

      case op: ProxyOp.MakeProxy =>
        F pure proxyStrategy.makeProxy(context, this, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)

      case op: MonadicOp.ExecuteEffect =>
        F widen effectStrategy.executeEffect[F](context, this, op)

      case op: MonadicOp.AllocateResource =>
        F widen resourceStrategy.allocateResource[F](context, this, op)
    }
  }

  override def execute(context: ProvisioningKeyProvider, step: WiringOp): Seq[NewObjectOp] = {
    step match {
      case op: WiringOp.UseInstance =>
        instanceStrategy.getInstance(context, this, op)

      case op: WiringOp.ReferenceKey =>
        instanceStrategy.getInstance(context, this, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, this, op)
    }
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

  private[this] def verifyEffectType[F[_]: TagK](ops: Vector[ExecutableOp])(addFailure: ProvisioningFailure => F[Unit])(implicit F: QuasiEffect[F]): F[Unit] = {
    val provisionerEffectType = SafeType.getK[F]
    val monadicOps = ops.collect { case m: MonadicOp => m }
    F.traverse_(monadicOps) {
      op =>
        val actionEffectType = op.effectHKTypeCtor
        val isEffect = actionEffectType != SafeType.identityEffectType

        if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
          addFailure(ProvisioningFailure(op, new IncompatibleEffectTypesException(op, provisionerEffectType, actionEffectType)))
        } else {
          F.unit
        }
    }
  }

}
