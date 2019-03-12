package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.LocatorDefaultImpl
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
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

  // HACK
  override def instantiate[F[_]: TagK](plan: OrderedPlan, parentContext: Locator)(implicit F: DIMonad[F]): F[Either[FailedProvision[F], Locator]] = {

    val provisioningContext = ProvisionActive()
    provisioningContext.instances.put(DIKey.get[Locator.LocatorRef], new Locator.LocatorRef())

    val excluded = mutable.Set[DIKey]()
    val failures = new mutable.ArrayBuffer[ProvisioningFailure]

    val mainAction: F[Unit] = F.traverse_(plan.steps) {
      step =>
        F.flatMap(F.maybeSuspend(excluded.contains(step.target))) {
          case true => F.unit
          case false =>
            val failureContext = ProvisioningFailureContext(parentContext, provisioningContext, step)

            // FIXME: MonadError / Sync [!?]
            val maybeResult = Try(execute[F](LocatorContext(provisioningContext.toImmutable, parentContext), step))
              .recoverWith(failureHandler.onExecutionFailed(failureContext).andThen(_.map(F.pure)))

            maybeResult match {
              case Success(s0) =>
                F.flatMap(s0) { s =>
                  F.maybeSuspend {
                    s.foreach {
                      res =>
                        val maybeSuccess = Try(interpretResult(provisioningContext, res))
                          .recoverWith(failureHandler.onBadResult(failureContext))

                        maybeSuccess match {
                          case Success(_) =>
                          case Failure(failure) =>
                            excluded ++= plan.topology.transitiveDependees(step.target)
                            failures += ProvisioningFailure(step, failure)
                            ()
                        }
                    }
                  }
                }

              case Failure(failure) =>
                F.maybeSuspend {
                  excluded ++= plan.topology.transitiveDependees(step.target)
                  failures += ProvisioningFailure(step, failure)
                  ()
                }
            }

        }
    }

    F.map(mainAction) { _ =>
      if (failures.nonEmpty) {
        // FIXME: add deallocators ???
        Left(FailedProvision[F](provisioningContext.toImmutable, plan, parentContext, failures.toVector, Seq(null.asInstanceOf[F[Unit]])))
      } else {
        val context = ProvisionImmutable(provisioningContext.instances, provisioningContext.imports)

        val locator = new LocatorDefaultImpl(plan, Option(parentContext), context)
        locator.get[Locator.LocatorRef].ref.set(locator)

        Right(locator)
      }
    }
  }

  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: ExecutableOp)(implicit F: DIMonad[F]): F[Seq[NewObjectOp]] = {
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

