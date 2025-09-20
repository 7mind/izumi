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
        case '{ StringContext.apply($parts*).s($args*) } =>
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

          @tailrec
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

          val (scParts, args) = parts.toSeq.partitionMap(_.left.map(s => Expr(s)))

          makeMessage(false, scParts, args)
        case Apply(Select(_, "stripMargin"), arg :: Nil) =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Select(Apply(Ident("augmentString"), arg :: Nil), "stripMargin") =>
          matchExpr(arg.asExprOf[String], multiline = true)
        case Literal(StringConstant(s)) =>
          val cval = Seq(Expr(s))
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

        case Literal(StringConstant(_)) =>
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
      new ArgumentNameExtractionMacro[qctx.type](strict).recoverArgName(expr.asTerm)
    }

    matchExpr(message, multiline = false)
  }
}
