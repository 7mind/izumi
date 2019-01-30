package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.LocatorDefaultImpl
import com.github.pshirshov.izumi.distage.model.Locator
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
  , failureHandler: ProvisioningFailureInterceptor
  , verifier: ProvisionOperationVerifier
) extends PlanInterpreter with OperationExecutor {
  override def instantiate(plan: OrderedPlan, parentContext: Locator): Either[FailedProvision, Locator] = {
    val excluded = mutable.Set[DIKey]()

    val provisioningContext = ProvisionActive()
    provisioningContext.instances.put(DIKey.get[Locator.LocatorRef], new Locator.LocatorRef())

    val failures = new mutable.ArrayBuffer[ProvisioningFailure]

    plan.steps.foreach {
      case step if excluded.contains(step.target) =>
      case step =>
        val failureContext = ProvisioningFailureContext(parentContext, provisioningContext, step)

        val maybeResult = Try(execute(LocatorContext(provisioningContext.toImmutable, parentContext), step))
          .recoverWith(failureHandler.onExecutionFailed(failureContext))

        maybeResult match {
          case Success(s) =>
            s.foreach {
              r =>
                val maybeSuccess = Try(interpretResult(provisioningContext, r))
                  .recoverWith(failureHandler.onBadResult(failureContext))

                maybeSuccess match {
                  case Success(_) =>
                  case Failure(f) =>
                    excluded ++= plan.topology.transitiveDependees(step.target)
                    failures += ProvisioningFailure(step, f)
                }
            }

          case Failure(f) =>
            excluded ++= plan.topology.transitiveDependees(step.target)
            failures += ProvisioningFailure(step, f)
        }
    }

    if (failures.nonEmpty) {
      Left(FailedProvision(provisioningContext.toImmutable, plan, parentContext, failures.toVector))
    } else {
      Right(ProvisionImmutable(provisioningContext.instances, provisioningContext.imports))
        .map {
          context =>
            val locator = new LocatorDefaultImpl(plan, Option(parentContext), context)
            locator.get[Locator.LocatorRef].ref.set(locator)
            locator
        }
    }
  }


  private def interpretResult(active: ProvisionActive, result: ExecutableOpResult): Unit = {
    result match {
      case ExecutableOpResult.NewImport(target, instance) =>
        verifier.verify(target, active.imports.keySet, instance, s"import")
        active.imports += (target -> instance)

      case ExecutableOpResult.NewInstance(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "instance")
        active.instances += (target -> instance)

      case ExecutableOpResult.UpdatedSet(target, instance) =>
        verifier.verify(target, active.instances.keySet, instance, "set")
        active.instances += (target -> instance)

      case ExecutableOpResult.DoNothing() =>
        ()
    }
  }


  def execute(context: ProvisioningKeyProvider, step: ExecutableOp): Seq[ExecutableOpResult] = {
    step match {
      case op: ImportDependency =>
        importStrategy.importDependency(context, op)

      case op: WiringOp.ReferenceInstance =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.ReferenceKey =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, this, op)

      case op: WiringOp.CallFactoryProvider =>
        factoryProviderStrategy.callFactoryProvider(context, this, op)

      case op: WiringOp.InstantiateClass =>
        classStrategy.instantiateClass(context, op)

      case op: CreateSet =>
        setStrategy.makeSet(context, op)

      case op: WiringOp.InstantiateTrait =>
        traitStrategy.makeTrait(context, op)

      case op: WiringOp.InstantiateFactory =>
        factoryStrategy.makeFactory(context, this, op)

      case op: ProxyOp.MakeProxy =>
        proxyStrategy.makeProxy(context, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)
    }
  }


}

