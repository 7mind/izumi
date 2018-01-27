package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.Locator
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{SetOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan}
import com.github.pshirshov.izumi.distage.provisioning.strategies._

import scala.collection.mutable
import scala.util.{Failure, Try}


class ProvisionerDefaultImpl
(
  provisionerHook: ProvisionerHook
  , provisionerIntrospector: ProvisionerIntrospector
  , setStrategy: SetStrategy
  , proxyStrategy: ProxyStrategy
  , factoryStrategy: FactoryStrategy
  , traitStrategy: TraitStrategy
  , providerStrategy: ProviderStrategy
  , classStrategy: ClassStrategy
  , importStrategy: ImportStrategy
  , customStrategy: CustomStrategy
  , instanceStrategy: InstanceStrategy
) extends Provisioner with OperationExecutor {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val activeProvision = ProvisionActive()

    val provisions = plan.steps.foldLeft(activeProvision) {
      case (active, step) =>
        execute(LocatorContext(active, parentContext), step).foldLeft(active) {
          case (acc, result) =>
            Try(interpretResult(active, result)) match {
              case Failure(f) =>
                throw new DIException(s"Provisioning unexpectedly failed on result handling for $step", f)
              case _ =>
                acc
            }
        }

    }

    val withImmutableSets = provisions.instances.map {
      case (key, value: mutable.HashSet[_]) =>
        (key, value.toSet)
      case v =>
        v
    }

    ProvisionImmutable(withImmutableSets, provisions.imports)
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

      case OpResult.SetElement(set, instance) =>
        if (set == instance) {
          throw new DIException(s"Pathological case. Tried to add set into itself: $set", null)
        }
        set += instance
    }
  }

  def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult] = {
    step match {
      case op: ExecutableOp.ImportDependency =>
        importStrategy.importDependency(context, op)

      case op: ExecutableOp.WiringOp.ReferenceInstance =>
        instanceStrategy.getInstance(context, op)

      case op: ExecutableOp.WiringOp.CallProvider =>
        providerStrategy.callProvider(context, op)

      case op: ExecutableOp.WiringOp.InstantiateClass =>
        classStrategy.instantiateClass(context, op)

      case op: ExecutableOp.SetOp.CreateSet =>
        setStrategy.makeSet(context, op)

      case op: SetOp.AddToSet =>
        setStrategy.addToSet(context, op)

      case t: ExecutableOp.WiringOp.InstantiateTrait =>
        traitStrategy.makeTrait(context, t)

      case f: WiringOp.InstantiateFactory =>
        factoryStrategy.makeFactory(context, this, f)

      case m: ExecutableOp.ProxyOp.MakeProxy =>
        proxyStrategy.makeProxy(context, m)

      case i: ExecutableOp.ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, i)

      case op: ExecutableOp.CustomOp =>
        customStrategy.handle(context, op)

    }
  }


}

