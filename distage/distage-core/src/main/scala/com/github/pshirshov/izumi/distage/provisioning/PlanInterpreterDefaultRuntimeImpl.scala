package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.LocatorDefaultImpl
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
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

, failureHandler: ProvisioningFailureInterceptor
, verifier: ProvisionOperationVerifier
) extends PlanInterpreter
     with OperationExecutor
     with WiringExecutor {

  override def instantiate[F[_]: TagK](plan: OrderedPlan, parentContext: Locator)(implicit F: DIEffect[F]): F[Either[FailedProvision[F], Locator]] = {

    val mutProvisioningContext = ProvisionActive()
    mutProvisioningContext.instances.put(DIKey.get[Locator.LocatorRef], new Locator.LocatorRef())

    val mutExcluded = mutable.Set.empty[DIKey]
    val mutFailures = mutable.ArrayBuffer.empty[ProvisioningFailure]
    val mutFinalizers = mutable.ArrayBuffer.empty[F[Unit]]

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

    mainAction.map { _ =>
//      F.maybeSuspend {
        if (mutFailures.nonEmpty) {
          // FIXME: add deallocators ???
          Left(FailedProvision[F](mutProvisioningContext.toImmutable, plan, parentContext, mutFailures.toVector, Seq(null.asInstanceOf[F[Unit]])))
        } else {
          val context = ProvisionImmutable(mutProvisioningContext.instances, mutProvisioningContext.imports)

          val locator = new LocatorDefaultImpl(plan, Option(parentContext), context)
          locator.get[Locator.LocatorRef].ref.set(locator)

          Right(locator)
        }
//      }
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
        // FIXME:
//        Seq()
        F widen effectStrategy.executeEffect[F](context, this, op)

      case op: MonadicOp.AllocateResource =>
        // FIXME: resource not implemented ???
        ???
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

  private[this] def interpretResult(active: ProvisionActive, result: NewObjectOp): Unit = {
    result match {
      case NewObjectOp.NewImport(target, instance) =>
        verifier.verify(target, active.imports.keySet, instance, s"import")
        active.imports += (target -> instance)

      case NewObjectOp.NewInstance(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "instance")
        active.instances += (target -> instance)

      case NewObjectOp.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)

      case NewObjectOp.DoNothing() =>
        ()
    }
  }

}

