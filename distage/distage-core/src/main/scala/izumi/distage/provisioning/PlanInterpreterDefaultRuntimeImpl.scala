package izumi.distage.provisioning

import java.util.concurrent.atomic.AtomicReference

import izumi.distage.LocatorDefaultImpl
import izumi.distage.model.Locator
import izumi.distage.model.definition.DIResource
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.exceptions.IncompatibleEffectTypesException
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, Finalizer, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionMutable
import izumi.distage.model.provisioning._
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.reflection._
import izumi.distage.model.recursive.LocatorRef
import izumi.reflect.TagK

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

// TODO: add introspection capabilities
class PlanInterpreterDefaultRuntimeImpl
(
  setStrategy: SetStrategy
, proxyStrategy: ProxyStrategy
, providerStrategy: ProviderStrategy
, importStrategy: ImportStrategy
, instanceStrategy: InstanceStrategy
, effectStrategy: EffectStrategy
, resourceStrategy: ResourceStrategy

, failureHandler: ProvisioningFailureInterceptor
, verifier: ProvisionOperationVerifier,
) extends PlanInterpreter with OperationExecutor {

  override def instantiate[F[_]: TagK](plan: OrderedPlan, parentContext: Locator, filterFinalizers: FinalizerFilter[F])(implicit F: DIEffect[F]): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    DIResource.make(
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


  private[this] def instantiateImpl[F[_]: TagK](plan: OrderedPlan, parentContext: Locator)(implicit F: DIEffect[F]): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F]()
    val locator = new LocatorDefaultImpl(plan, Option(parentContext), mutProvisioningContext)
    val locatorRef = new LocatorRef(new AtomicReference(Left(locator)))
    mutProvisioningContext.instances.put(DIKey.get[LocatorRef], locatorRef)

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]

    def processStep(step: ExecutableOp): F[Unit] = {
      F.maybeSuspend {
        mutExcluded.contains(step.target)
      }.flatMap {
        skipped =>
          if (skipped) {
            F.unit
          } else {
            val failureContext = ProvisioningFailureContext(parentContext, mutProvisioningContext, step)

            F.definitelyRecover[Try[Seq[NewObjectOp]]](
              action =
                execute(LocatorContext(mutProvisioningContext.toImmutable, parentContext), step).map(Success(_))
            )(recover =
                exception =>
                  F.maybeSuspend {
                    failureHandler.onExecutionFailed(failureContext)
                      .applyOrElse(exception, Failure(_: Throwable))
                  }
            ).flatMap {
              case Success(newObjectOps) =>
                F.maybeSuspend {
                  newObjectOps.foreach {
                    newObject =>
                      val maybeSuccess = Try(interpretResult(mutProvisioningContext, newObject))
                        .recoverWith(failureHandler.onBadResult(failureContext))

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
                }
            }
          }
      }
    }

    val (imports, otherSteps) = plan.steps.partition {
      case _: ImportDependency => true
      case _ => false
    }

    for {
      _ <- F.traverse_(imports)(processStep)
      _ <- verifyEffectType[F](otherSteps, addFailure = f => F.maybeSuspend(mutFailures += f))

      failedImportsOrEffects <- F.maybeSuspend(mutFailures.nonEmpty)
      immutable = mutProvisioningContext.toImmutable
      res <- if (failedImportsOrEffects) {
        F.maybeSuspend(Left(FailedProvision[F](immutable, plan, parentContext, mutFailures.toVector))): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]]
      } else {
        F.traverse_(otherSteps)(processStep)
          .flatMap { _ =>
            F.maybeSuspend {
              if (mutFailures.nonEmpty) {
                Left(FailedProvision[F](immutable, plan, parentContext, mutFailures.toVector))
              } else {
                val finalLocator = new LocatorDefaultImpl(plan, Option(parentContext), immutable)
                val res = Right(finalLocator)
                locatorRef.ref.set(res)
                res
              }
            }
        }
      }
    } yield res
  }

  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: ExecutableOp)(implicit F: DIEffect[F]): F[Seq[NewObjectOp]] = {
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

      case r@NewObjectOp.NewResource(target, instance, _) =>
        verifier.verify(target, active.instances.keySet, instance, "resource")
        active.instances += (target -> instance)
        val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case r@NewObjectOp.NewFinalizer(target, _) =>
        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
        active.finalizers prepend Finalizer[F](target, finalizer)

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)
    }
  }

  private[this] def verifyEffectType[F[_]: TagK](ops: Vector[ExecutableOp], addFailure: ProvisioningFailure => F[Unit])(implicit F: DIEffect[F]): F[Unit] = {
    val provisionerEffectType = SafeType.getK[F]
    val monadicOps = ops.collect { case m: MonadicOp => m }
    F.traverse_(monadicOps) {
      op =>
        val actionEffectType = op.effectHKTypeCtor
        val isEffect = actionEffectType != SafeType.identityEffectType

        if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
          addFailure(ProvisioningFailure(op, new IncompatibleEffectTypesException(provisionerEffectType, actionEffectType)))
        } else {
          F.unit
        }
    }
  }

}

