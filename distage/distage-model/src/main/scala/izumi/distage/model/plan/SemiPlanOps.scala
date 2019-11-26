package izumi.distage.model.plan

import izumi.distage.model.Locator
import izumi.distage.model.plan.ExecutableOp.ImportDependency
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
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }
}
