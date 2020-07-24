package izumi.distage.model.plan.impl

import izumi.distage.model.Locator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.ExecutableOp.WiringOp.CallProvider
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.SemiPlan
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection._
import izumi.reflect.Tag

private[plan] trait SemiPlanOps extends Any { this: SemiPlan =>

  override final def toSemi: SemiPlan = this

  override final def resolveImport[T: Tag](instance: T): SemiPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override final def resolveImport[T: Tag](id: Identifier)(instance: T): SemiPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override final def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlanOps.resolveImports1(AbstractPlanOps.importToInstances(f), steps))
  }

  override final def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[SemiplanOp]]): SemiPlan = {
    SemiPlan(steps = AbstractPlanOps.resolveImports1(f, steps.toVector), roots)
  }

  final def providerImport[T](function: Functoid[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target.tpe == function.get.ret =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get), i.origin))
    }
  }

  final def providerImport[T](id: Identifier)(function: Functoid[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target == DIKey.TypeKey(function.get.ret).named(id) =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get), i.origin))
    }
  }
}
