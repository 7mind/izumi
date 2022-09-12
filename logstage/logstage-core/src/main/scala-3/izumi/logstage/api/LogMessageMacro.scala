package izumi.logstage.api

import izumi.logstage.api.Log.Message

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object LogMessageMacro {
  def message( message: Expr[String])(using qctx: Quotes): Expr[Message] = {
    import qctx.reflect.*
    @tailrec
    def matchTerm(message: Term, multiline: Boolean): Expr[Message] = {
      message match {
        case Inlined(_, _, term) =>
          matchTerm(term, multiline)
        case Block(_, term) =>
          matchTerm(term, multiline)
        case Apply(Select(_, "stripMargin"), arg :: Nil) =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Literal(c) =>
          println(s"just const: $c")
          ???
        case _ =>
          println(("FAIL0", message)) //, message.getClass, message.show, message.asTerm, message.value))
          ???
      }
    }

    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      import qctx.reflect.*
      message match {
        case '{ _root_.scala.StringContext.apply ($parts: _*).s ($args: _*) } =>
          println (s"ok: ${message.show}")
          ???
        case '{ null } =>
          println (s"NULL")
          ???
        case o =>
          matchTerm(o.asTerm, multiline)
      }
    }

    matchExpr(message, multiline = false)
  }
}
