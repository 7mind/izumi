package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.LocatorDefaultImpl
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.provisioning.Provision.ProvisionMutable
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

// TODO: add introspection capabilities
class PlanInterpreterDefaultRuntimeImpl
(
  setStrategy: SetStrategy
, proxyStrategy: ProxyStrategy
, factoryStrategy: FactoryStrategy
, traitStrategy: TraitStrategy
, factoryProviderStrategy: FactoryProviderStrategy
, providerStrategy: ProviderStrategy
, classStrategy: ClassStrategy
, importStrategy: ImportStrategy
, instanceStrategy: InstanceStrategy
, effectStrategy: EffectStrategy
, resourceStrategy: ResourceStrategy

, failureHandler: ProvisioningFailureInterceptor
, verifier: ProvisionOperationVerifier
) extends PlanInterpreter
     with OperationExecutor
     with WiringExecutor {

  // FIXME: _do not_ expose InnerResourceType; have two separate functions
  override def instantiate[F[_]: TagK](plan: OrderedPlan, parentContext: Locator)(implicit F: DIEffect[F]): DIResourceBase[F, Locator] { type InnerResource <: Either[FailedProvision[F], Locator] } = {
//    DIResource.make(
//      acquire = instantiate0(plan, parentContext)
//    )(release = {
//      resource =>
//        val finalizers = resource match {
//          case Left(failedProvision) => failedProvision.failed.finalizers
//          case Right(locator) => locator.dependencyMap.finalizers
//        }
//        F.traverse_(finalizers) {
//          case (_, eff) => F.maybeSuspend(eff()).flatMap(identity)
//        }
//    })
//      // FIXME: evalMap[F.fail[???]]
//      .map {
//      _.throwOnFailure()
//    }
    new DIResourceBase[F, Locator] {
      override type InnerResource = Either[FailedProvision[F], LocatorDefaultImpl[F]]
      override def allocate: F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
        instantiate0(plan, parentContext)
      }
      override def deallocate(resource: Either[FailedProvision[F], LocatorDefaultImpl[F]]): F[Unit] = {
        val finalizers = resource match {
          case Left(failedProvision) => failedProvision.failed.finalizers
          case Right(locator) => locator.dependencyMap.finalizers
        }
        F.traverse_(finalizers) {
          case (_, eff) => F.maybeSuspend(eff()).flatMap(identity)
        }
      }

      override def extract(resource: Either[FailedProvision[F], LocatorDefaultImpl[F]]): Locator = {
        resource.throwOnFailure() // FIXME ??? shitty extractor
      }
    }
  }

  def instantiate0[F[_]: TagK](plan: OrderedPlan, parentContext: Locator)(implicit F: DIEffect[F]): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
    val mutProvisioningContext = ProvisionMutable[F]()
    mutProvisioningContext.instances.put(DIKey.get[Locator.LocatorRef], new Locator.LocatorRef())

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]

    val mainAction: F[Unit] = F.traverse_(plan.steps) {
      step =>
        F.maybeSuspend {
          mutExcluded.contains(step.target)
        }.flatMap {
          skipped =>
            if (skipped) {
              F.unit
            } else {
              val failureContext = ProvisioningFailureContext(parentContext, mutProvisioningContext, step)

              val maybeResult = F.definitelyRecover[Try[Seq[NewObjectOp]]](
                action = execute(LocatorContext(mutProvisioningContext.toImmutable, parentContext), step).map(Success(_))
              , recover = exception =>
                  F.maybeSuspend(failureHandler.onExecutionFailed(failureContext).applyOrElse(exception, Failure(_: Throwable)))
              )

              maybeResult.flatMap {
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
                            ()
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

    mainAction.flatMap { _ =>
      F.maybeSuspend {
        val context = mutProvisioningContext.toImmutable

        if (mutFailures.nonEmpty) {
          Left(FailedProvision[F](context, plan, parentContext, mutFailures.toVector))
        } else {
          val locator = new LocatorDefaultImpl(plan, Option(parentContext), context)
          locator.get[Locator.LocatorRef].ref.set(locator)

          Right(locator)
        }
      }
    }
  }

  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: ExecutableOp)(implicit F: DIEffect[F]): F[Seq[NewObjectOp]] = {
    step match {
      case op: ImportDependency =>
        F pure importStrategy.importDependency(context, op)

      case op: CreateSet =>
        F pure setStrategy.makeSet(context, op)

      case op: WiringOp =>
        F pure execute(context, op)

      case op: ProxyOp.MakeProxy =>
        F pure proxyStrategy.makeProxy(context, op)

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
      case op: WiringOp.ReferenceInstance =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.ReferenceKey =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, op)

      case op: WiringOp.InstantiateClass =>
        classStrategy.instantiateClass(context, op)

      case op: WiringOp.InstantiateTrait =>
        traitStrategy.makeTrait(context, op)

      case op: WiringOp.CallFactoryProvider =>
        factoryProviderStrategy.callFactoryProvider(context, this, op)

      case op: WiringOp.InstantiateFactory =>
        factoryStrategy.makeFactory(context, this, op)
    }
  }

  private[this] def interpretResult[F[_]](active: ProvisionMutable[F], result: NewObjectOp): Unit = {
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
        active.finalizers prepend (target -> finalizer)

      case r@NewObjectOp.NewFinalizer(target, _) =>
        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
        active.finalizers prepend target -> finalizer

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)

      case NewObjectOp.DoNothing() =>
        ()
    }
  }

}

