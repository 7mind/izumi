package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.commons.StringUtils
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.plan.Wiring.FactoryMethod


object FormattingUtils {

  def doFormat(target: DIKey, deps: Wiring): String = {
    val op = doFormat(deps)
    s"$target := $op"

  }

  private def doFormat(deps: Wiring): String = {
    import UnaryWiring._
    deps match {
      case Constructor(instanceType, _, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "make", ('[', ']'), ('(', ')'))

      case Abstract(instanceType, associations) =>
        doFormat(instanceType.tpe.toString, associations.map(_.format), "impl", ('[', ']'), ('{', '}'))

      case Function(instanceType, associations) =>
        doFormat(instanceType.toString, associations.map(_.format), "call", ('(', ')'), ('{', '}'))

      case FactoryMethod(factoryType, unaryWireables) =>
        val wirings = unaryWireables.map {
          w =>
            StringUtils.shift(s"${w.factoryMethod} ~= ${doFormat(w.wireWith)}", 2)
        }

        doFormat(
          factoryType.toString
          , wirings
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
      sb.append(StringUtils.shift(depRepr.mkString(s"\n"), 2))
    }
    sb.append(s"\n${delim._2}")

    sb.toString()
  }
}
