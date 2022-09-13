package izumi.logstage.api

import izumi.logstage.api.Log.Message

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

//Apply(
//  Select(
//    Apply(Select(New(Select(Select(Select(Ident(scala),collection),immutable),StringOps)),<init>),List(Inlined(EmptyTree,List(),Inlined(EmptyTree,List(),Ident(x$proxy1))))),stripMargin),
//  List(Inlined(EmptyTree,List(),Inlined(EmptyTree,List(),Literal(Constant(|)))))
//)

//List(Apply(Select(Apply(Select(Select(Select(Ident(_root_),scala),StringContext),apply),List(Typed(SeqLiteral(List(Literal(Constant(Running test...
//  |
//  |Test plan: )), Literal(Constant()))

//Apply(
//    Select(
//        Apply(
//            Select(Select(Select(Ident(_root_),scala),StringContext),apply)
object LogMessageMacro {
  def message( message: Expr[String])(using qctx: Quotes): Expr[Message] = {
    import qctx.reflect.*
    @tailrec
    def matchTerm(message: Term, multiline: Boolean): Expr[Message] = {
      message match {
        case Typed(term, _) =>
          matchTerm(term, multiline)
        case Inlined(_, _, term) =>
          matchTerm(term, multiline)
        case Block(_, term) =>
          matchTerm(term, multiline)
        case Apply(Select(_, "stripMargin"), arg :: Nil) =>
          report.warning(s"${System.nanoTime()} WIP: $arg;; $message")
          matchExpr(arg.asExprOf[String], multiline = true)
        case Select(Apply(Ident("augmentString"), arg :: Nil), "stripMargin") =>

          matchExpr(arg.asExprOf[String], multiline = true)

        case Literal(c) =>
          val cval = Expr.ofSeq(Seq(Expr(c.toString)))
          makeMessage(cval , Seq.empty)
        case _ =>
          println(("FAIL0", message)) //, message.getClass, message.show, message.asTerm, message.value))
          report.errorAndAbort(s"Failed to process $message")
      }
    }

//    def unpackArg()

    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      import qctx.reflect.*
      message match {
        case sc@'{ StringContext.apply ($parts: _*).s($args: _*) } =>
//          println (s"ok: ${message.show}, $sc")
          makeMessage(parts, Seq.empty)

        case '{ null } =>
          val cval = Expr.ofSeq(Seq(Expr("null")))
          makeMessage('{ Seq("null") }, Seq.empty)

        case o =>
          matchTerm(o.asTerm, multiline)
      }
    }

    def makeMessage(parts: Expr[Seq[String]], args: Seq[Expr[izumi.logstage.api.Log.LogArg]]): Expr[Message] = {
      val sc: Expr[StringContext] = '{ StringContext($parts: _*) }
      //val args: Seq[Expr[izumi.logstage.api.Log.LogArg]] = Expr.ofSeq(args)

      '{ Message($sc, ${Expr.ofSeq(args)}) }
    }

    matchExpr(message, multiline = false)
  }
}
