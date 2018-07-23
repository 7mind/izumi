package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._


object FormattingUtils {

  def doFormat(target: DIKey, deps: Wiring): String = {
    val op = doFormat(deps)
    s"$target := $op"
  }

  private def doFormat(deps: Wiring): String = {
    deps match {
      case Constructor(instanceType, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "make", ('[', ']'), ('(', ')'))

      case AbstractSymbol(instanceType, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "impl", ('[', ']'), ('{', '}'))

      case Function(instanceType, associations) =>
        doFormat(instanceType.toString, associations.map(_.format), "call", ('(', ')'), ('{', '}'))

      case FactoryMethod(factoryType, wireables, dependencies) =>
        val wirings = wireables.map {
          w =>
            s"${w.factoryMethod}: ${w.factoryMethod.finalResultType} ~= ${doFormat(w.wireWith)}".shift(2)
        }

        val depsRepr = dependencies.map(_.format)

        doFormat(
          factoryType.toString
          , wirings ++ depsRepr
          , "factory", ('(', ')'), ('{', '}')
        )

      case FactoryFunction(factoryType, wireables, dependencies) =>
        val wirings = wireables.map {
          case (idx, w) =>
            s"${w.factoryMethod}[$idx]: ${w.factoryMethod.finalResultType} ~= ${doFormat(w.wireWith)}".shift(2)
        }.toSeq

        val depsRepr = dependencies.map(_.format)

        doFormat(
          factoryType.toString
          , wirings ++ depsRepr
          , "call factory", ('(', ')'), ('{', '}')
        )

      case other =>
        s"UNEXPECTED WIREABLE: $other"
    }
  }

  def doFormat(impl: String, depRepr: Seq[String], opName: String, opFormat: (Char, Char), delim: (Char, Char)): String = {
    val sb = new StringBuilder()
    sb.append(s"$opName${opFormat._1}$impl${opFormat._2} ${delim._1}\n")
    if (depRepr.nonEmpty) {
      sb.append(depRepr.mkString(s"\n").shift(2))
    }
    sb.append(s"\n${delim._2}")

    sb.toString()
  }
}
