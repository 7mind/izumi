package izumi.distage.model.plan

import izumi.distage.model.Locator
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.WiringOp.CallProvider
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait ExtendedPlanAPI[OpType <: ExecutableOp] {
  this: AbstractPlan[OpType] =>
  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan[OpType]

  def resolveImport[T: Tag](instance: T): AbstractPlan[OpType]

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan[OpType]

  def locateImports(locator: Locator): AbstractPlan[OpType]

  /** Get all imports (unresolved dependencies).
    *
    * Note, presence of imports does not automatically mean that a plan is invalid,
    * Imports may be fulfilled by a `Locator`, by BootstrapContext, or they may be materialized by a custom
    * [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
    * */
  final def getImports: Seq[ImportDependency] =
    steps.collect { case i: ImportDependency => i }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    SemiPlan(steps = AbstractPlan.resolveImports(f, steps.toVector), gcMode)
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

  final def keys: Set[DIKey] = {
    steps.map(_.target).toSet
  }

  final def filter[T: Tag]: Seq[ExecutableOp] = {
    steps.filter(_.target == DIKey.get[T])
  }

  final def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.map(f), gcMode)
  }

  final def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.flatMap(f), gcMode)
  }

  final def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.collect(f), gcMode)
  }

  final def ++(that: AbstractPlan[OpType]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    val that0 = that.toSemi
    SemiPlan(steps ++ that0.steps, gcMode)
  }

  final def collectChildren[T: Tag]: Seq[ExecutableOp] = {
    val parent = SafeType.get[T]
    steps.filter {
      op =>
        val maybeChild = ExecutableOp.instanceType(op)
        maybeChild <:< parent
    }
  }

  final def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  def toSemi: SemiPlan = {
    val safeSteps = steps.flatMap {
      case _: InitProxy =>
        Seq.empty
      case i: MakeProxy =>
        Seq(i.op)
      case o => Seq(o)
    }
    SemiPlan(safeSteps.toVector, gcMode)
  }
}
