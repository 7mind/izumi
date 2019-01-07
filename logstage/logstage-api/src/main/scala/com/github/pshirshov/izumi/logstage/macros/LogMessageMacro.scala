package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.logstage.api.Log.{LogArg, Message}

import scala.reflect.macros.blackbox

object LogMessageMacro {

  def logMessageMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Message] = {
    import c.universe._

    message.tree match {
      // qq causes a weird warning here
      //case q"scala.StringContext.apply($stringContext).s(..$args)" =>
      case Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]) =>
        val namedArgs = ArgumentNameExtractionMacro.recoverArgNames(c)(args.map(p => c.Expr(p)))
        reifyContext(c)(stringContext, namedArgs)

      case Literal(c.universe.Constant(s)) =>
        val emptyArgs = reify(List(LogArg(Seq("@type"), "const", hidden = false)))
        val sc = q"StringContext(${s.toString})"
        reifyContext(c)(sc, emptyArgs)

      case other =>
        c.warning(other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()}.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |1) Simple variable: logger.log(s"My message: $$argument")
             |2) Chain: logger.log(s"My message: $${call.method} $${access.value}")
             |3) Named expression: logger.log(s"My message: $${Some.expression -> "argname"}")
             |4) Hidden arg expression: logger.log(s"My message: $${Some.expression -> "argname" -> null}")
             |""".stripMargin)

        val emptyArgs = reify {
          val repr = c.Expr[String](Literal(Constant(c.universe.showCode(other)))).splice
          List(LogArg(Seq("@type"), "expr", hidden = false), LogArg(Seq("@expr"), repr, hidden = false))
        }
        val sc = q"StringContext($other)"
        reifyContext(c)(sc, emptyArgs)
    }
  }

  private[this] def reifyContext(c: blackbox.Context)(stringContext: c.universe.Tree, namedArgs: c.Expr[List[LogArg]]): c.Expr[Message] = {
    import c.universe._
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice
        , namedArgs.splice
      )
    }
  }

}
