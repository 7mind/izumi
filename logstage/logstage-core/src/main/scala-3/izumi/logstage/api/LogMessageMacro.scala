package izumi.logstage.api

import izumi.logstage.api.Log.{LogArg, Message}
import izumi.logstage.api.rendering.LogstageCodec

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

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
          matchExpr(arg.asExprOf[String], multiline = true)
        case Select(Apply(Ident("augmentString"), arg :: Nil), "stripMargin") =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Literal(c) =>
          val cval = Expr.ofSeq(Seq(Expr(c.toString)))
          makeMessage(cval , Seq.empty)
        case _ =>
          report.errorAndAbort(s"Failed to process $message")
      }
    }


    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      import qctx.reflect.*
      message match {
        case sc@'{ StringContext.apply ($parts: _*).s($args: _*) } =>
        makeMessage(parts, makeArgs(args))

        case '{ null } =>
          val cval = Expr.ofSeq(Seq(Expr("null")))
          makeMessage('{ Seq("null") }, Seq.empty)

        case o =>
          matchTerm(o.asTerm, multiline)
      }
    }

    def makeMessage(parts: Expr[Seq[String]], args: Seq[Expr[LogArg]]): Expr[Message] = {
      val sc: Expr[StringContext] = '{ StringContext($parts: _*) }
      '{ Message($sc, ${Expr.ofSeq(args)}) }
    }

    def makeArgs(args: Expr[Seq[Any]]): Seq[Expr[LogArg]] = {
      import scala.quoted.Varargs
      args match {
        case Varargs(a) =>
          a.map(makeArg)
        case _ =>
          report.errorAndAbort(s"Arguments expected but got: $args")
      }
    }

    def makeArg(expr: Expr[Any]): Expr[LogArg] = {
      val hidden: Expr[Boolean] = Expr(false)
      val codec: Expr[Option[LogstageCodec[Any]]] = Expr(None)
      val vals: Expr[Seq[String]] = Expr.ofSeq(extractArgName(Seq.empty, expr).map(n => Expr(n)))
      '{ LogArg($vals, $expr, $hidden, $codec)}
    }

    def extractArgName(acc: Seq[String], expr: Expr[Any]): Seq[String] = {
      expr match {
        case '{ ($expr: Any) -> $id } =>
          id.asTerm match {
            case Literal(c) =>
              acc :+ id.toString
            case _ =>
              report.errorAndAbort(s"Not an expected argument id: $id")
          }

        case o =>
          extractTermName(acc, o.asTerm)
      }
    }

    @tailrec
    def extractTermName(acc: Seq[String], term: Term): Seq[String] = {
      term match {
        case Ident(name) =>
          acc :+ name
        case Literal(c) =>
          acc :+ c.toString
        case This(idt) =>
          idt match {
            case Some(id) =>
              acc :+ id
            case None =>
              acc :+ "this"
          }

        case Select(Ident("scala"), "Predef") =>
          acc

        case Select(e, s) => // ${x.value}
          extractTermName(s +: acc, e)

        case Apply(Select(e,s), Nil) => // ${x.getSomething}
          extractTermName(s +: acc, e)

        case Apply(Select(e, _), Ident(s) :: Nil) => // ${Predef.ops(x).getSomething}
          extractTermName(s +: acc, e)

        case _ =>
          report.warning(s"Cannot extract argument name from: ${term.show}, tree: $term")
          acc :+ s"EXPRESSION:${term.show}"

      }
    }

    matchExpr(message, multiline = false)
  }
}
