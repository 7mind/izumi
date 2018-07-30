package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}



// TODO: add introspection capabilities
class ProvisionerDefaultImpl
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
) extends Provisioner with OperationExecutor {
  override def provision(plan: OrderedPlan, parentContext: Locator): ProvisionImmutable = {
    val excluded = mutable.Set[DIKey]()

    val provisioingContext = ProvisionActive()
    provisioingContext.instances.put(DIKey.get[Locator.LocatorRef], new Locator.LocatorRef())

    val failures = new mutable.HashMap[DIKey, mutable.Set[Throwable]] with mutable.MultiMap[DIKey, Throwable]

    plan.steps.foreach {
      case step if excluded.contains(step.target) =>
      case step =>
        val failureContext = ProvisioningFailureContext(parentContext, provisioingContext, step)

        val maybeResult = Try(execute(LocatorContext(provisioingContext.toImmutable, parentContext), step))
          .recoverWith(failureHandler.onExecutionFailed(failureContext))

        maybeResult match {
          case Success(s) =>
            s.foreach {
              r =>
                val maybeSuccess = Try(interpretResult(provisioingContext, r))
                  .recoverWith(failureHandler.onBadResult(failureContext))

                maybeSuccess match {
                  case Success(_) =>
                  case Failure(f) =>
                    excluded ++= plan.topology.transitiveDependees(step.target)
                    failures.addBinding(step.target, f)
                }
            }

          case Failure(f) =>
            excluded ++= plan.topology.transitiveDependees(step.target)
            failures.addBinding(step.target, f)
        }
    }

    if (failures.nonEmpty) {
      failureHandler.onProvisioningFailed(provisioingContext.toImmutable, plan, parentContext, failures.mapValues(_.toSet).toMap)
    } else {
      ProvisionImmutable(provisioingContext.instances, provisioingContext.imports)
    }
  }

  private def interpretResult(active: ProvisionActive, result: OpResult): Unit = {
    result match {
      case OpResult.NewImport(target, value) =>
        value match {
          case _ if active.imports.contains(target) =>
            throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
          case opResult: OpResult =>
            throw new TriedToAddSetIntoSetException(s"Pathological case. Tried to add set into itself: $target -> $value", target, opResult)
          case _ =>
            active.imports += (target -> value)
        }

      case OpResult.NewInstance(target, value) =>
        value match {
          case _ if active.instances.contains(target) =>
            throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
          case opResult: OpResult =>
            throw new TriedToAddSetIntoSetException(s"Pathological case. Tried to add set into itself: $target -> $value", target, opResult)
          case _ =>
            active.instances += (target -> value)
        }

      case OpResult.UpdatedSet(target, instance) =>
        active.instances += (target -> instance)

      case OpResult.DoNothing() =>
        ()
    }
  }

  def execute(context: ProvisioningKeyProvider, step: ExecutableOp): Seq[OpResult] = {
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

