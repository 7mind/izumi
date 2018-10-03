package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.functional.Renderable



trait CompactPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val uniqueClassNames = plan
      .steps
      .flatMap(stepTypes)
      .flatMap(simpleNames)
      .groupBy(_._1)
      .map(p => (p._1, p._2.toSet))
      .filter(_._2.size == 1)
      .values
      .flatten
      .toSet

    plan
      .steps
      .map(_.toString)
      .map(step =>
        uniqueClassNames.foldLeft(step) {
          case (s, (simpleName, fullName)) => s.replaceAll(fullName, simpleName)
        }
      )
      .mkString("\n")
  }


  private def stepTypes(op: ExecutableOp): Seq[SafeType] = op match {
    case ImportDependency(target, references, _) =>
      Seq(target.tpe) ++ references.map(_.tpe)
    case CreateSet(target, tpe, members, _) =>
      Seq(target.tpe, tpe) ++ members.map(_.tpe)
    case v: WiringOp =>
      Seq(v.target.tpe) ++ wiringDeps(v.wiring)
    case MakeProxy(o, forwardRefs, _) =>
      stepTypes(o) ++ forwardRefs.map(_.tpe)
    case InitProxy(target, deps, proxy, _) =>
      Seq(target.tpe) ++ deps.map(_.tpe) ++ stepTypes(proxy)
  }

  private def wiringDeps(deps: Wiring): Seq[SafeType] = {
    deps match {
      case Constructor(instanceType, associations, prefix) =>
        Seq(instanceType) ++ associations.map(_.tpe) ++ prefix.toSeq.map(_.tpe)

      case AbstractSymbol(instanceType, associations, prefix) =>
        Seq(instanceType) ++ associations.map(_.tpe) ++ prefix.toSeq.map(_.tpe)

      case Function(instanceType, associations) =>
        providerDeps(instanceType) ++ associations.map(_.tpe)

      case FactoryMethod(factoryType, wireables, dependencies) =>
        Seq(factoryType) ++ wireables.flatMap(_.methodArguments).map(_.tpe) ++ dependencies.map(_.tpe)

      case FactoryFunction(factoryType, wireables, dependencies) =>
        providerDeps(factoryType) ++ wireables.values.toSeq.flatMap(_.methodArguments).map(_.tpe) ++ dependencies.map(_.tpe)

      case _ => Seq()
    }
  }

  private def providerDeps(provider: Provider): Seq[SafeType] = {
    provider.associations.map(_.tpe) ++ provider.argTypes
  }

  private def simpleNames(value: SafeType): Map[String, String] = {
    Seq(value.tpe.typeSymbol.name.toString -> value.tpe.typeSymbol.fullName) ++
      value.tpe.typeArgs.map(arg => arg.typeSymbol.name.toString -> arg.typeSymbol.fullName)
  }.toMap
}

object CompactPlanFormatter {
  implicit object OrderedPlanFormatter extends CompactPlanFormatter
}
