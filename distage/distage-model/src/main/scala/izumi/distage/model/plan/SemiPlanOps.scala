package izumi.distage.model.plan

import izumi.distage.model.Locator
import izumi.distage.model.plan.ExecutableOp.WiringOp.CallProvider
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait SemiPlanOps {
  this: SemiPlan =>

  override def toSemi: SemiPlan = this

  override def resolveImport[T: Tag](instance: T): SemiPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): SemiPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports1(AbstractPlan.importToInstances(f), steps))
  }

  override def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[SemiplanOp]]): SemiPlan = {
    SemiPlan(steps = AbstractPlan.resolveImports1(f, steps.toVector), gcMode)
  }

  final def providerImport[T](function: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target.tpe == function.get.ret =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get, function.get.associations), i.origin))
    }
  }

  final def providerImport[T](id: String)(function: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target == DIKey.IdKey(function.get.ret, id) =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get, function.get.associations), i.origin))
    }
  }
}
