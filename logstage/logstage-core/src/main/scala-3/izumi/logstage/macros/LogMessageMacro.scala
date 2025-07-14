package izumi.logstage.macros

import izumi.logstage.api.Log.{LogArg, Message, StrictMessage}
import izumi.logstage.api.rendering.LogstageCodec

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.compiletime
import scala.compiletime.{codeOf, erasedValue}
import scala.quoted.{Expr, Quotes, Type}

object LogMessageMacro {
  transparent inline def createMessageWithMode[EncMode <: Singleton](inline message: String): Message = {
    inline erasedValue[EncMode] match {
      case _: EncodingMode.NonStrict.type => Message(message)
      case _: EncodingMode.Strict.type => StrictMessage(message)
      case _: EncodingMode.Raw.type => Message.raw(message)
      case _ =>
        compiletime.error(
          "Couldn't match " + codeOf(erasedValue[EncMode]) + " with any of the values in EncodingMode enum, expected one of: NonStrict, Strict or Raw"
        )
    }
  }

  def message(message: Expr[String], strict: Boolean)(using qctx: Quotes): Expr[Message] = {
    import qctx.reflect.*

    def matchExpr(message: Expr[String], multiline: Boolean): Expr[Message] = {
      message match {
        case sc @ '{ StringContext.apply($parts*).s($args*) } =>
          import scala.quoted.Varargs
          val partsSeq = parts match {
            case Varargs(a) =>
              a
            case _ =>
              report.errorAndAbort(s"String context expected but got: $parts")
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
        case Inlined(_, _, term) =>
          matchTerm(term, multiline)
        case Typed(term, _) =>
          matchTerm(term, multiline)
        case Block(_, term) =>
          matchTerm(term, multiline)
        case Apply(Select(left, "+"), right :: Nil) =>
          val unpacked = unpackPlus(left, List(right))
          assert(unpacked.nonEmpty)
          val parts = ArrayBuffer.empty[Either[String, Expr[LogArg]]]

          def collectPart(part: Term, parts: ArrayBuffer[Either[String, Expr[LogArg]]]): Unit = {
            part match {
              case Inlined(_, _, term) => collectPart(term, parts)
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
                  case None => parts += Left(c.value.toString)
                }
              case chunk @ Ident(_) =>
                val expr = Right(makeArg(chunk.asExprOf[Any]))
                parts.lastOption match {
                  case Some(value) =>
                    value match {
                      case Left(value) => parts += expr
                      case Right(value) => parts ++= Seq(Left(""), expr)
                    }
                  case None => parts ++= Seq(Left(""), expr)
                }
              case chunk @ Apply(_, _) =>
                val expr = Right(makeArg(chunk.asExprOf[Any]))
                parts.lastOption match {
                  case Some(value) =>
                    value match {
                      case Left(value) => parts += expr
                      case Right(value) => parts ++= Seq(Left(""), expr)
                    }
                  case None => parts ++= Seq(Left(""), expr)
                }
            }
          }

          unpacked.foreach(collectPart(_, parts))

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
          makeMessage(multiline, cval, Seq.empty)
        case _ =>
          report.errorAndAbort(s"Failed to process $message")
      }
    }

    @tailrec
    def unpackPlus(message: Term, parts: List[Term]): List[Term] = {
      message match {
        case Inlined(_, _, tree) =>
          unpackPlus(tree, parts)

        case Ident(_) =>
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
        parts.map(s => '{ ${ s }.stripMargin })
      } else {
        parts
      })

      val sc: Expr[StringContext] = '{ StringContext(${ scparts }*) }
      '{ Message(${ sc }, ${ Expr.ofSeq(args) }) }
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
      '{ LogArg(${ vals }, ${ realExpr }, ${ Expr(isHidden) }, ${ codec }) }
    }

    def findCodec(expr: Expr[Any]): Expr[Option[LogstageCodec[Any]]] = {
      val targ = expr.asTerm.tpe.widenTermRefByName
      val sym = Symbol.requiredClass("izumi.logstage.api.rendering.LogstageCodec")
      val appliedType = AppliedType(sym.typeRef, List(targ))
      Implicits.search(appliedType) match {
        case s: ImplicitSearchSuccess =>
          val c = s.tree.asExprOf[LogstageCodec[?]]
          '{ Some(${ c }.asInstanceOf[LogstageCodec[Any]]) }
        case _ if strict =>
          report.errorAndAbort(s"Implicit search failed for ${appliedType.show} for ${expr.show}, LogstageCodec instances are required in Strict mode")
        case _ =>
          Expr(None)
      }
    }

    def extractArgName(acc: Seq[String], expr0: Expr[Any]): (Seq[String], Expr[Any], Boolean, Expr[Option[LogstageCodec[Any]]]) = {
      def nameOf(id: Expr[Any]): Seq[String] = {
        id.asTerm match {
          case Literal(c) =>
            acc :+ c.value.toString
          case _ =>
            report.errorAndAbort(s"Log argument name must be a literal: $id")
        }
      }
      expr0 match {
        case '{ ($expr: Any) -> $id -> null } =>
          (nameOf(id), expr, true, findCodec(expr))

        case '{ ($expr: Any) -> null } =>
          (extractTermName(acc, expr.asTerm), expr, true, findCodec(expr))

        case '{ ($expr: Any) -> $id } =>
          (nameOf(id), expr, false, findCodec(expr))

        case other =>
          (extractTermName(acc, other.asTerm), other, false, findCodec(other))
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

        case Apply(Select(e, s), Nil) => // ${x.getSomething}
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
