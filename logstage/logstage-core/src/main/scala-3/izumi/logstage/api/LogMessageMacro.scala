package izumi.logstage.api

import izumi.logstage.api.Log.{LogArg, Message}
import izumi.logstage.api.rendering.LogstageCodec

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

object LogMessageMacro {
  def message(message: Expr[String], strict: Boolean)(using qctx: Quotes): Expr[Message] = {
    import qctx.reflect.*

    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      message match {
        case sc@'{ StringContext.apply ($parts: _*).s($args: _*) } =>
          import scala.quoted.Varargs
          val partsSeq = parts match {
            case Varargs (a) =>
              a
            case _ =>
              report.errorAndAbort (s"String context expected but got: $parts")
          }

          makeMessage(multiline, partsSeq, makeArgs(args))

        case '{ null } =>
          makeMessage(false, Seq(Expr("null")), Seq.empty)

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

          makeMessage(false, scParts, args)
        case Apply(Select(_, "stripMargin"), arg :: Nil) =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Select(Apply(Ident("augmentString"), arg :: Nil), "stripMargin") =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Literal(c) =>
          val cval = Seq(Expr(c.value.toString))
          makeMessage(multiline, cval , Seq.empty)
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

    def makeMessage(multiline: Boolean, parts: Seq[Expr[String]], args: Seq[Expr[LogArg]]): Expr[Message] = {
      val scparts = Expr.ofSeq(if (multiline) {
        parts.map(s => '{ $s.stripMargin })
      } else {
        parts
      })

      val sc: Expr[StringContext] = '{ StringContext($scparts: _*) }
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
      val (parts, realExpr, isHidden, codec) = extractArgName(Seq.empty, expr)
      val vals: Expr[Seq[String]] = Expr.ofSeq(parts.map(n => Expr(n)))
      '{ LogArg($vals, $realExpr, ${ Expr(isHidden) }, $codec)}
    }

    def findCodec(expr: Expr[Any]): Expr[Option[LogstageCodec[Any]]] = {
      val codec: Expr[Option[LogstageCodec[Any]]] = expr.asTerm.tpe.asType match {
        case '[a] =>
          Expr.summon[LogstageCodec[a]].asInstanceOf[Option[Expr[LogstageCodec[Any]]]] match {
            case Some(c) =>
              '{ Some($c.asInstanceOf[LogstageCodec[Any]]) }
            case None if strict =>
              report.errorAndAbort(s"Can't find LogstageCodec for ${expr.show} but we are in Strict mode")
            case None =>
              Expr(None)
          }
      }

      // Expr.summon mysteriously fail here
//      val codec = Implicits.search(expr.asTerm.tpe) match {
//        case iss: ImplicitSearchSuccess =>
//          report.warning(s"Found codec for ${expr.asTerm}: ${expr.asTerm.tpe} ==> ${iss.tree.asExpr}")
//          '{Some(${iss.tree.asExpr.asInstanceOf[Expr[LogstageCodec[Any]]]})}
//        case isf: ImplicitSearchFailure =>
//          report.warning(s"Not found codec for ${expr.asTerm}: ${expr.asTerm.tpe}")
//          Expr(None)
//      }

      codec
    }

    def extractArgName(acc: Seq[String], expr: Expr[Any]): (Seq[String], Expr[Any], Boolean, Expr[Option[LogstageCodec[Any]]]) = {
      def nameOf(id: Expr[Any]): Seq[String] = {
        id.asTerm match {
          case Literal(c) =>
            acc :+ c.value.toString
          case _ =>
            report.errorAndAbort(s"Log argument name must be a literal: $id")
        }
      }
      expr match {
        case '{ ($expr: Any) -> null} =>
          (extractTermName(acc, expr.asTerm), expr, true, findCodec(expr))

        case '{ ($expr: Any) -> $id -> null } =>
          (nameOf(id), expr, true, findCodec(expr))

        case '{ ($expr: Any) -> $id } =>
          (nameOf(id), expr, false, findCodec(expr))

        case o =>
          (extractTermName(acc, o.asTerm), o, false, findCodec(o))
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
