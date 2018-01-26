package org.bitbucket.pshirshov.izumi

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class Message(template: StringContext, args: List[(String, Any)])

trait InterpolatorMacros {

  implicit class LogSC(val sc: StringContext) {
    def l(args: Any*): Message = macro InterpolatorMacros.fetchArgsNames
  }

}

object InterpolatorMacros extends InterpolatorMacros {

  val recoverConstantsPrefix = "unknown_"

  def fetchArgsNames(c: Context)(args: c.Expr[Any]*): c.Expr[Message] = {
    import c.universe._

    val expressions = args.map { param =>
      param.tree match {
        case c.universe.Literal(c.universe.Constant(_)) => {
          reify {
            (s"$recoverConstantsPrefix${param.splice}", param.splice)
          }.tree
        }
        case c.universe.Select(_, TermName(s)) => {
          val paramRepTree = c.Expr[String](Literal(Constant(s)))
          reify {
            (paramRepTree.splice, param.splice)
          }.tree
        }
      }
    }

    reify {
      Message(
        c.prefix.splice.asInstanceOf[LogSC].sc,
        c.Expr[List[(String, Any)]](Apply(Select(reify(List).tree, newTermName("apply")), expressions.toList)).splice
      )
    }

  }
}

