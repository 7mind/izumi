package izumi.logstage.api

import izumi.logstage.api.Log.{LogArg, Message}
import izumi.logstage.api.rendering.LogstageCodec

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object LogMessageMacro {
  def message( message: Expr[String])(using qctx: Quotes): Expr[Message] = {
    import qctx.reflect.*

    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      import qctx.reflect.*
      message match {
//        case sc@'{ ($left:Any) + ($right: Any) } =>
//          report.errorAndAbort(s"Failed to process +: $message")

        case sc@'{ StringContext.apply ($parts: _*).s($args: _*) } =>
          makeMessage(multiline, parts, makeArgs(args))

        case '{ null } =>
          val cval = Expr.ofSeq(Seq(Expr("null")))
          makeMessage(false, '{ Seq("null") }, Seq.empty)

        case o =>
          matchTerm(o.asTerm, multiline)
      }
    }


    @tailrec
    def matchTerm(message: Term, multiline: Boolean): Expr[Message] = {
      message match {
        case Typed(term, _) =>
          matchTerm(term, multiline)
        case Inlined(_, _, term) =>
          matchTerm(term, multiline)
        case Block(_, term) =>
          matchTerm(term, multiline)
        case Apply(Select(left, "+"), right :: Nil) =>
          val unpacked = unpackPlus(left, List(right))
          assert(unpacked.nonEmpty)
          val parts = scala.collection.mutable.ArrayBuffer.empty[Either[String, Expr[LogArg]]]

          unpacked.foreach {
            case Literal(c) =>
              parts.lastOption match {
                case Some(value) =>
                  value match {
                    case Left(value) =>
                      parts.remove(parts.size - 1)
                      parts += Left(value + c.value.toString)
                    case Right(_) =>
                      parts += Left(c.value.toString)
                  }
                case None =>
                  parts += Left(c.value.toString)
              }
            case chunk@Ident(_) =>
              val expr = Right(makeArg(chunk.asExprOf[Any]))
              parts.lastOption match {
                case Some(value) =>
                  value match {
                    case Left(value) =>
                      parts += expr
                    case Right(value) =>
                      parts ++= Seq(Left(""), expr)
                  }
                case None =>
                  parts ++= Seq(Left(""), expr)
              }
          }

          assert(parts.nonEmpty)
          if (parts.last.isRight) {
            parts += Left("")
          }

          val scParts = parts.collect {
            case Left(s) =>
              Expr(s)
          }.toSeq
          val args = parts.collect {
            case Right(a) =>
              a
          }.toSeq

          makeMessage(false, Expr.ofSeq(scParts), args)
        case Apply(Select(_, "stripMargin"), arg :: Nil) =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Select(Apply(Ident("augmentString"), arg :: Nil), "stripMargin") =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Literal(c) =>
          val cval = Expr.ofSeq(Seq(Expr(c.value.toString)))
          makeMessage(false, cval , Seq.empty)
        case _ =>
          report.errorAndAbort(s"Failed to process $message")
      }
    }

    @tailrec
    def unpackPlus(message: Term, parts: List[Term]): List[Term] = {
      message match {
        case Ident(i) =>
          message +: parts

        case Literal(c) =>
          message +: parts

        case Apply(Select(left, "+"), right :: Nil) =>
          unpackPlus(left, right +: parts)

        case _ =>
          report.errorAndAbort(s"Concatenation is too complex for analysis, use string interpolation instead: ${message.show}")
      }
    }

    def makeMessage(multiline: Boolean, parts: Expr[Seq[String]], args: Seq[Expr[LogArg]]): Expr[Message] = {
      // TODO: multiline
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
      val (parts, isHidden) = extractArgName(Seq.empty, expr)

      val codec: Expr[Option[LogstageCodec[Any]]] = expr.asTerm.tpe.asType match {
        case '[a] =>
          Expr.summon[LogstageCodec[a]].asInstanceOf[Option[Expr[LogstageCodec[Any]]]] match {
            case Some(c) =>
              '{ Some($c) }
            case None =>
              Expr(None)
          }
      }

      // Expr.summon mysteriously fail here
//      val codec = Implicits.search(expr.asTerm.tpe) match {
//        case iss: ImplicitSearchSuccess =>
//          '{Some(${iss.tree.asExpr.asInstanceOf[Expr[LogstageCodec[Any]]]})}
//        case isf: ImplicitSearchFailure =>
//          Expr(None)
//      }

      val vals: Expr[Seq[String]] = Expr.ofSeq(parts.map(n => Expr(n)))
      '{ LogArg($vals, $expr, ${ Expr(isHidden) }, $codec)}
    }

    def extractArgName(acc: Seq[String], expr: Expr[Any]): (Seq[String], Boolean) = {
      def nameOf(id: Expr[Any]): Seq[String] = {
        id.asTerm match {
          case Literal(c) =>
            acc :+ c.value.toString
          case _ =>
            report.errorAndAbort(s"Log argument name must be a literal: $id")
        }
      }
      expr match {
        case '{ ($expr: Any) -> $id -> null } =>
          (nameOf(id), true)

        case '{ ($expr: Any) -> $id } =>
          (nameOf(id), false)

        case o =>
          (extractTermName(acc, o.asTerm), false)
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
