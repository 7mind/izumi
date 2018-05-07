package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan}
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._

import scala.util.{Failure, Try}


class ProvisionerDefaultImpl
(
  setStrategy: SetStrategy
  , proxyStrategy: ProxyStrategy
  , factoryStrategy: FactoryStrategy
  , traitStrategy: TraitStrategy
  , providerStrategy: ProviderStrategy
  , classStrategy: ClassStrategy
  , importStrategy: ImportStrategy
  , customStrategy: CustomStrategy
  , instanceStrategy: InstanceStrategy
// TODO: add introspection capabilities
//  , provisionerHook: ProvisionerHook
//  , provisionerIntrospector: ProvisionerIntrospector
//  , loggerHook: LoggerHook
) extends Provisioner with OperationExecutor {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val activeProvision = ProvisionActive()

    val provisions = plan.steps.foldLeft(activeProvision) {
      case (active, step) =>
        execute(LocatorContext(active.toImmutable, parentContext), step).foldLeft(active) {
          case (acc, result) =>
            Try(interpretResult(active, result)) match {
              case Failure(f) =>
                throw new DIException(s"Provisioning unexpectedly failed on result handling for $step", f)
              case _ =>
                acc
            }
        }
    }

    ProvisionImmutable(provisions.instances, provisions.imports)
  }

  private def interpretResult(active: ProvisionActive, result: OpResult): Unit = {
    result match {
      case OpResult.NewImport(target, value) =>
        if (active.imports.contains(target)) {
          throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
        }
        if (value.isInstanceOf[OpResult]) {
          throw new DIException(s"Pathological case. Tried to add set into itself: $target -> $value", null)
        }
        active.imports += (target -> value)

      case OpResult.NewInstance(target, value) =>
        if (active.instances.contains(target)) {
          throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
        }
        if (value.isInstanceOf[OpResult]) {
          throw new DIException(s"Pathological case. Tried to add set into itself: $target -> $value", null)
        }
        active.instances += (target -> value)

      case OpResult.UpdatedSet(target, instance) =>
        active.instances += (target -> instance)

      case OpResult.DoNothing() =>
        ()
    }
  }

  def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult] = {
    step match {
      case op: ImportDependency =>
        importStrategy.importDependency(context, op)

      case op: WiringOp.ReferenceInstance =>
        instanceStrategy.getInstance(context, op)

      case op: WiringOp.CallProvider =>
        providerStrategy.callProvider(context, this, op)

      case op: WiringOp.InstantiateClass =>
        classStrategy.instantiateClass(context, op)

      case op: SetOp.CreateSet =>
        setStrategy.makeSet(context, op)

      case op: WiringOp.InstantiateTrait =>
        traitStrategy.makeTrait(context, op)

      case op: WiringOp.InstantiateFactory =>
        factoryStrategy.makeFactory(context, this, op)

      case op: ProxyOp.MakeProxy =>
        proxyStrategy.makeProxy(context, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)

      case op: CustomOp =>
        customStrategy.handle(context, op)

    }
  }


}

