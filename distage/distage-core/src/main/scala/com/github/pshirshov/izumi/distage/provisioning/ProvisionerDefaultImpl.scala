package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp._
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan}
import com.github.pshirshov.izumi.distage.model.provisioning._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._

import scala.util.{Failure, Success, Try}


// TODO: add introspection capabilities
class ProvisionerDefaultImpl
(
  setStrategy: SetStrategy
  , proxyStrategy: ProxyStrategy
  , factoryStrategy: FactoryStrategy
  , traitStrategy: TraitStrategy
  , providerStrategy: ProviderStrategy
  , classStrategy: ClassStrategy
  , importStrategy: ImportStrategy
  , instanceStrategy: InstanceStrategy
  , failureHandler: ProvisioningFailureInterceptor
) extends Provisioner with OperationExecutor {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val provisions = plan.steps.foldLeft(ProvisionActive()) {
      case (active, step) =>

        val context = ProvisioningFailureContext(parentContext, active, step)

        Try(execute(LocatorContext(active.toImmutable, parentContext), step))
          .recoverWith(failureHandler.onExecutionFailed(context)) match {
          case Success(results) =>
            results.foldLeft(active) {
              case (acc, result) =>
                Try(interpretResult(active, result))
                  .recoverWith(failureHandler.onBadResult(context)) match {
                  case Success(_) =>
                    acc
                  case Failure(f) =>
                    failureHandler.onStepOperationFailure(context, result, f)
                }
            }


          case Failure(f) =>
            failureHandler.onStepFailure(context, f)
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
        // FIXME ???
        import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
        providerStrategy.callProvider(context, this,
          WiringOp.CallProvider(op.target, Wiring.UnaryWiring.Function(op.wiring.provider, op.wiring.providerArguments)))

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

