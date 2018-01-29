package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.Wiring._
import com.github.pshirshov.izumi.distage.model.plan.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._


object FormattingUtils {

  def doFormat(target: DIKey, deps: Wiring): String = {
    val op = doFormat(deps)
    s"$target := $op"

  }

  private def doFormat(deps: Wiring): String = {
    deps match {
      case Constructor(instanceType, _, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "make", ('[', ']'), ('(', ')'))

      case Abstract(instanceType, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "impl", ('[', ']'), ('{', '}'))

      case Function(instanceType, associations) =>
        doFormat(instanceType.toString, associations.map(_.format), "call", ('(', ')'), ('{', '}'))

      case FactoryMethod(factoryType, unaryWireables, fmDeps) =>
        val wirings = unaryWireables.map {
          w =>
            s"${w.factoryMethod}: ${w.factoryMethod.returnType} ~= ${doFormat(w.wireWith)}".shift(2)
        }

        val depsRepr = fmDeps.map(_.format)

        doFormat(
          factoryType.toString
          , wirings ++ depsRepr
          , "fact", ('(', ')'), ('{', '}')
        )

      case other =>
        s"UNEXPECTED WIREABLE: $other"
    }
  }

  private def doFormat(impl: String, depRepr: Seq[String], opName: String, opFormat: (Char, Char), delim: (Char, Char)): String = {
    val sb = new StringBuilder()
    sb.append(s"$opName${opFormat._1}$impl${opFormat._2} ${delim._1}\n")
    if (depRepr.nonEmpty) {
      sb.append(depRepr.mkString(s"\n").shift(2))
    }
    sb.append(s"\n${delim._2}")

    sb.toString()
  }
}
