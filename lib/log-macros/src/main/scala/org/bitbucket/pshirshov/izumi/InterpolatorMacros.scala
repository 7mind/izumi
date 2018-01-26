package org.bitbucket.pshirshov.izumi

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait InterpolatorMacros {
  implicit class LogSC(val sc: StringContext) {
    def l_macr(args: Any*): (List[(String, Any)]) = macro InterpolatorMacros.fetchArgsNames
  }
}

object InterpolatorMacros extends InterpolatorMacros {

  val recoverConstantsPrefix = "unknown_"

  def fetchArgsNames(c : Context)(args : c.Expr[Any]*) : c.Expr[List[(String, Any)]] = {
    import c.universe._

    val expressions = args.map { param =>
      param.tree match {
        case c.universe.Literal(c.universe.Constant(_)) => {
          reify { (s"$recoverConstantsPrefix${param.splice}", param.splice) }.tree
        }
        case c.universe.Select(_, TermName(s)) => {
          val paramRepTree = c.Expr[String](Literal(Constant(s)))
          reify { (paramRepTree.splice, param.splice) }.tree
        }
      }
    }
    val listApply = Select(reify(List).tree, newTermName("apply"))
    c.Expr[List[(String, Any)]](Apply(listApply,  expressions.toList))
  }
}
