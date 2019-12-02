package izumi.distage.provisioning.strategies

import distage.DIKey
import izumi.distage.model.exceptions.{InvalidPlanException, UnexpectedProvisionResultException}
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.plan.ExecutableOp.WiringOp.CallFactoryProvider
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.provisioning.NewObjectOp.{NewImport, NewInstance}
import izumi.distage.model.provisioning.strategies.{FactoryExecutor, FactoryProviderStrategy}
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.FactoryFunction.FactoryMethod
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class FactoryProviderStrategyDefaultImpl extends FactoryProviderStrategy {
  def callFactoryProvider(context: ProvisioningKeyProvider, executor: WiringExecutor, op: CallFactoryProvider): Seq[NewObjectOp.NewInstance] = {

    val args: Seq[TypedRef[_]] = op.wiring.factoryCtorParameters.map {
      param =>
        context.fetchKey(param.key, param.isByName) match {
          case Some(dep) =>
            TypedRef(dep, param.key.tpe)
          case _ if param.key == DIKey.get[FactoryExecutor] =>
            TypedRef(mkExecutor(context, executor, op.wiring.factoryIndex, op))
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: Class: $op.target, dependency: $param")
        }
    }

    val instance = op.wiring.provider.unsafeApply(args: _*)
    Seq(NewObjectOp.NewInstance(op.target, instance))
  }

  private def mkExecutor(context: ProvisioningKeyProvider, executor: WiringExecutor, factoryIndex: Map[Int, FactoryMethod], op: CallFactoryProvider): FactoryExecutor = {
    new FactoryExecutor {
      override def execute(methodId: Int, args: Seq[Any]): Any = {
        val FactoryMethod(_, productWiring, methodArguments) = factoryIndex(methodId)

        val productDeps = productWiring.requiredKeys
        val narrowedContext = context.narrow(productDeps)

        val argsWithKeys = methodArguments.zip(args).toMap
        val extendedContext = narrowedContext.extend(argsWithKeys)

        val results = executor.execute(extendedContext, mkExecutableOp(op.target, productWiring, op.origin)).toList
        results match {
          case List(i: NewInstance) =>
            i.instance
          case List(i: NewImport) =>
            i.instance
          case List(_) =>
            throw new UnexpectedProvisionResultException(s"Factory returned a result class other than NewInstance or NewImport in $results", results)
          case _ :: _ =>
            throw new UnexpectedProvisionResultException(s"Factory returned more than one result in $results", results)
          case Nil =>
            throw new UnexpectedProvisionResultException(s"Factory empty result list: $results", results)
        }
      }
    }
  }

  private[this] def mkExecutableOp(key: DIKey, functionWiring: SingletonWiring.Function, origin: OperationOrigin): WiringOp.CallProvider = {
    val target = DIKey.ProxyElementKey(key, functionWiring.instanceType)
    WiringOp.CallProvider(target, functionWiring, origin)
  }

}

