package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.{CallProvider, ReferenceInstance}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.functional.Renderable

// TODO: we need to parameterize plans with op types to avoid possibility of having proxy ops in semiplan
sealed trait AbstractPlan {
  def definition: ModuleBase

  def steps: Seq[ExecutableOp]

  def roots: Set[DIKey]

  /** Get all imports (unresolved dependencies).
    *
    * Note, presence of imports does not automatically mean that a plan is invalid,
    * Imports may be fulfilled by a `Locator`, by BootstrapContext, or they may be materialized by a custom
    * [[com.github.pshirshov.izumi.distage.model.provisioning.strategies.ImportStrategy]]
    * */
  final def getImports: Seq[ImportDependency] =
    steps.collect { case i: ImportDependency => i }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    SemiPlan(definition, steps = AbstractPlan.resolveImports(f, steps.toVector), roots)
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

  final def keys: Set[DIKey] = steps.map(_.target).toSet

  final def toSemi: SemiPlan = {
    val safeSteps = steps.flatMap {
      case _: InitProxy =>
        Seq.empty
      case i: MakeProxy =>
        Seq(i.op)
      case o => Seq(o)
    }
    SemiPlan(definition, safeSteps.toVector, roots)
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def resolveImport[T: Tag](instance: T): AbstractPlan

  def filter[T: Tag]: Seq[ExecutableOp] = {
    steps.filter(_.target == DIKey.get[T])
  }

  def collectChildren[T: Tag]: Seq[ExecutableOp] = {
    val parent = SafeType.get[T]
    steps.filter {
      op =>
        val maybeChild = ExecutableOp.instanceType(op)
        maybeChild weak_<:< parent
    }
  }

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan

  final def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  override def toString: String = {
    steps.map(_.toString).mkString("\n")
  }
}

object AbstractPlan {
  private[plan] def resolveImports(f: PartialFunction[ImportDependency, Seq[ExecutableOp]], steps: Vector[ExecutableOp]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  private[plan] def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))))
}

/**
  * An unordered plan.
  *
  * You can turn into an [[OrderedPlan]] via [[com.github.pshirshov.izumi.distage.model.Planner.finish]]
  */
final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp], roots: Set[DIKey]) extends AbstractPlan {
  lazy val index: Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    SemiPlan(definition, steps.map(f).toVector, roots)
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.flatMap(f).toVector, roots)
  }

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.collect(f).toVector, roots)
  }

  def ++(that: AbstractPlan): SemiPlan = {
    SemiPlan(definition ++ that.definition, steps.toVector ++ that.steps, roots)
  }

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
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }

}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], roots: Set[DIKey], topology: PlanTopology) extends AbstractPlan {
  override def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }
}

object OrderedPlan {
  implicit val defaultFormatter: CompactPlanFormatter.OrderedPlanFormatter.type = CompactPlanFormatter.OrderedPlanFormatter

  implicit class PlanSyntax(r: OrderedPlan) {
    def render()(implicit ev: Renderable[OrderedPlan]): String = ev.render(r)
  }

}
