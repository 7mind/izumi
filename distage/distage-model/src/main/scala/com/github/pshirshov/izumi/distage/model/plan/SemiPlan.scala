package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.{CallProvider, ReferenceInstance}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring
import com.github.pshirshov.izumi.distage.model.util._

trait PlanRenderer {
  def render(strings: Seq[Format]): String
}
case class CompactPlanRenderer() extends PlanRenderer {
  override def render(strings: Seq[Format]): String = {
    val notUniqueClassNames = strings.flatMap(_.args)
      .collect {
        case DIKeyArg(value) => value.tpe.getClass
        case ClassArg(value) => value
      }
      .groupBy(simpleName)
      .map(p => (p._1, p._2.toSet))
      .filter(_._2.size > 1).keys.toSet

    val result = strings.map { s =>
      val args = s.args.map {
        case DIKeyArg(v) =>
          if (notUniqueClassNames.contains(simpleName(v.tpe.getClass)))
            AnyArg(v.toString)
          else
            AnyArg(simpleName(v.tpe.getClass))
        case ClassArg(v) =>
          if (notUniqueClassNames.contains(simpleName(v)))
            AnyArg(v.toString)
          else
            AnyArg(simpleName(v))
        case arg => arg
      }

      Format(s.format, args: _*)
    }.map(_.render())

    result.mkString("\n")
  }

  private def simpleName(value: Class[_]) = {
    value.getName.substring(value.getName.lastIndexOf('.') + 1)
  }
}

sealed trait AbstractPlan {
  def definition: ModuleBase
  def steps: Seq[ExecutableOp]

  lazy val index: Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  def map(f: ExecutableOp => ExecutableOp): SemiPlan

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan

  def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def resolveImport[T: Tag](instance: T): AbstractPlan

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan

  def providerImport[T](f: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target.tpe == f.get.ret =>
        Seq(CallProvider(i.target, UnaryWiring.Function(f.get, f.get.associations), i.origin))
    }
  }

  def providerImport[T](id: String)(f: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target == DIKey.IdKey(f.get.ret, id) =>
        Seq(CallProvider(i.target, UnaryWiring.Function(f.get, f.get.associations), i.origin))
    }
  }

  override def toString: String = {
    CompactPlanRenderer().render(steps.map(_.format))
//    val result = steps.map(_.format).map(_.render())
//    result.mkString("\n")
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

/** Unordered plan. You can turn into an [[OrderedPlan]] by using [[com.github.pshirshov.izumi.distage.model.Planner#finish]] **/
final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp]) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    copy(steps = steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    copy(steps = steps.flatMap(f))
  }

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    copy(steps = steps.collect(f))
  }

  def resolveImport[T: Tag](instance: T): SemiPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  def resolveImport[T: Tag](id: String)(instance: T): SemiPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports(f, steps))
  }

  def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }

}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], topology: PlanTopology) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    SemiPlan(definition, steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.flatMap(f))
  }

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.collect(f))
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    SemiPlan(definition, steps = AbstractPlan.resolveImports(f, steps))
  }

  def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }
}
