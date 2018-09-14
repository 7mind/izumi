package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Wiring}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.util.Format
import com.github.pshirshov.izumi.distage.model.util.Format._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

object FormattingUtils {

  def doFormat(target: DIKey, deps: Wiring, origin: Option[Binding]): Format = {
    val op = doFormat(deps)
    val pos = formatBindingPosition(origin)
    Format("%s %s := %s", target, pos, op)
  }

  def formatBindingPosition(origin: Option[Binding]): String = {
    origin.map(_.origin.toString) getOrElse "(<unknown>)"
  }

  private def doFormat(deps: Wiring): Format = {
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
            // shift
            Format("%s: %s ~= %s", w.factoryMethod, w.factoryMethod.finalResultType, doFormat(w.wireWith))
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
            Format()
            s"${w.factoryMethod}[$idx]: ${w.factoryMethod.finalResultType} ~= ${doFormat(w.wireWith)}".shift(2)
        }.toSeq

        val depsRepr = dependencies.map(_.format).map(_.toString)

        doFormat(
          factoryType.toString
          , wirings ++ depsRepr
          , "call factory", ('(', ')'), ('{', '}')
        )

      case other =>
        s"UNEXPECTED WIREABLE: $other"
    }
  }

  def doFormat(impl: String, depRepr: Seq[Format], opName: String, opFormat: (Char, Char), delim: (Char, Char)): Format = {
    Format("%s%s%s%s $s $s", opName, opFormat._1, impl, opFormat._2, delim._1, depRepr)
    val sb = new StringBuilder()
//    sb.append(s"$opName${opFormat._1}$impl${opFormat._2} ${delim._1}\n")
    if (depRepr.nonEmpty) {
      sb.append(depRepr.mkString("\n").shift(2))
    }
    sb.append(s"\n${delim._2}")

    sb.toString()
  }
}
