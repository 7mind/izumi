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
        val emptyArgs = reify(List.empty)
        val sc = q"StringContext(${s.toString})"
        reifyContext(c)(sc, emptyArgs)

      case other =>
        c.warning(other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()} ; ${showRaw(other)}.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |${ArgumentNameExtractionMacro.example}
             |""".stripMargin)

        val emptyArgs = reify(List.empty)
        /* reify {
          val repr = c.Expr[String](Literal(Constant(c.universe.showCode(other)))).splice
          ...
        */
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
