package izumi.logstage.macros

import izumi.logstage.api.Log.LogArg

import scala.annotation.tailrec
import scala.reflect.macros.blackbox
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.rendering.LogstageCodec

import scala.util.{Failure, Success}


class ArgumentNameExtractionMacro[C <: blackbox.Context](final val c: C, strict: Boolean) {
  private final val applyDebug = DebugProperties.`izumi.debug.macro.logstage`.asBoolean(false)

  @inline private[this] final def debug(arg: c.universe.Tree, s: => String): Unit = {
    if (applyDebug) {
      c.warning(arg.pos, s)
    }
  }

  import ExtractedName._

  val example: String =
    s"""1) Simple variable:
       |   logger.info(s"My message: $$argument")
       |2) Chain:
       |   logger.info(s"My message: $${call.method} $${access.value}")
       |3) Named expression:
       |   logger.info(s"My message: $${Some.expression -> "argname"}")
       |4) Named expression, hidden name:
       |   logger.info(s"My message: $${Some.expression -> "argname" -> null}")
       |5) De-camelcased name:
       |   logger.info($${camelCaseName-> ' '})
       |""".stripMargin


  protected[macros] def recoverArgNames(args: Seq[c.Tree]): c.Expr[List[LogArg]] = {
    import c.universe._



    object Arrow {
      def unapply(arg: c.universe.Tree): Option[TypeApply] = {
        arg match {
          case t@TypeApply(Select(Select(Ident(TermName("scala")), _), TermName("ArrowAssoc")), List(TypeTree())) => Some(t)
          case _ => None
        }
      }
    }

    object ArrowPair {
      def unapply(arg: c.universe.Tree): Option[(c.universe.Tree, c.universe.Tree)] = {
        arg match {
          case Apply(TypeApply(
          Select(
          Apply(Arrow(_), leftExpr :: Nil),
          _ /*TermName("$minus$greater")*/
          )
          , List(TypeTree())
          )
          , List(rightExpr)
          ) => Some((leftExpr, rightExpr))
          case _ => None
        }
      }
    }


    object ArrowArg {
      def unapply(arg: c.universe.Tree): Option[(c.Expr[Any], ExtractedName)] = {
        arg match {
          case ArrowPair(expr, Literal(Constant(char: Char))) => // ${value -> ' '}
            Some((c.Expr(expr), NChar(char)))
          case ArrowPair(expr, Literal(Constant(name: String))) => // ${value -> "name"}
            Some((c.Expr(expr), NString(name)))
          case ArrowPair(expr@NameSeq(names), Literal(Constant(null))) => // ${value -> null}
            Some((c.Expr(expr), NString(names.last)))
          case _ =>
            None
        }
      }
    }

    object HiddenArrowArg {
      def unapply(arg: c.universe.Tree): Option[(c.Expr[Any], ExtractedName)] = {
        arg match {
          case ArrowPair(expr, Literal(Constant(null))) => // ${value -> "name" -> null}
            ArrowArg.unapply(expr)

          case _ =>
            None
        }
      }
    }

    object NameSeq {
      def unapply(arg: c.universe.Tree): Option[Seq[String]] = {
        extract(arg, Seq.empty)
      }


      @tailrec
      private def extract(arg: c.universe.Tree, acc: Seq[String]): Option[Seq[String]] = {
        arg match {
          case c.universe.Select(Ident(TermName("scala")), TermName("Predef")) =>
            debug(arg, s"END-PREDEF")
            Some(acc)

          case c.universe.Select(e, TermName(s)) => // ${x.value}
            debug(arg, s"B1: arg=${c.universe.showRaw(arg)} e=${c.universe.showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case Apply(c.universe.Select(e, TermName(s)), List()) => // ${x.getSomething}
            debug(arg, s"B2: arg=${c.universe.showRaw(arg)} e=${c.universe.showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case Apply(c.universe.Select(e, _), Ident(TermName(s)) :: Nil) => // ${Predef.ops(x).getSomething}
            debug(arg, s"B2-1: arg=${c.universe.showRaw(arg)} e=${c.universe.showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case c.universe.This(TypeName(s)) =>
            debug(arg, s"END-THIS: arg=${c.universe.showRaw(arg)} s='$s', acc=$acc")
            if (s.isEmpty) {
              Some("this" +: acc)
            } else {
              Some(s +: acc)
            }

          case c.universe.Ident(TermName(s)) =>
            debug(arg, s"END-NAME: arg=${c.universe.showRaw(arg)} s='$s', acc=$acc")
            Some(s +: acc)

          case _ =>
            debug(arg, s"END-NONE, arg=${c.universe.showRaw(arg)}, acc=$acc")
            None
        }
      }
    }

    val expressions = args.map(a => c.Expr(a)).map {
      param =>
        param.tree match {
          case NameSeq(seq) =>
            reifiedExtracted(param, seq)

          case ArrowArg(expr, name) => // ${x -> "name"}
            name match {
              case NChar(ch) if ch == ' ' =>
                expr.tree match {
                  case NameSeq(seq) =>
                    reifiedExtracted(expr, seq.map(_.camelToUnderscores.replace('_', ' ')))
                  case _ =>
                    reifiedExtracted(expr, Seq(ch.toString))
                }
              case NChar(ch) =>
                c.abort(param.tree.pos,
                  s"""Unsupported mapping: $ch
                     |
                     |You have the following ways to assign a name:
                     |$example
                     |""".stripMargin)

              case NString(s) =>
                reifiedExtracted(expr, Seq(s))
            }

          case HiddenArrowArg(expr, name) => // ${x -> "name" -> null }
            reifiedExtractedHidden(expr, name.str)

          case t@c.universe.Literal(c.universe.Constant(v)) => // ${2+2}
            c.warning(t.pos,
              s"""Constant expression as a logger argument: $v, this makes no sense.
                 |
                 |But Logstage expects you to use string interpolations instead, such as:
                 |$example
                 |""".stripMargin)

            reifiedPrefixed(param, "UNNAMED")

          case v =>
            c.warning(v.pos,
              s"""Expression as a logger argument: $v
                 |
                 |But Logstage expects you to use string interpolations instead, such as:
                 |$example
                 |
                 |Tree: ${c.universe.showRaw(v)}
                 |""".stripMargin)
            reifiedPrefixedValue(c.Expr[String](Literal(Constant(c.universe.showCode(v)))), param, "EXPRESSION")
        }
    }

    c.Expr[List[LogArg]] {
      q"List(..$expressions)"
    }
  }


  private def reifiedPrefixed(param: c.Expr[Any], prefix: String): c.universe.Expr[LogArg] = {
    reifiedPrefixedValue(param, param, prefix)
  }

  private def reifiedPrefixedValue(param: c.Expr[Any], value: c.Expr[Any], prefix: String): c.universe.Expr[LogArg] = {
    import c.universe._
    val prefixRepr = c.Expr[String](Literal(Constant(prefix)))
    reify {
      LogArg(Seq(s"${prefixRepr.splice}:${param.splice}"), value.splice, hiddenName = false, findCodec(param).splice)
    }
  }


  private def reifiedExtractedHidden(param: c.Expr[Any], s: String): c.universe.Expr[LogArg] = {
    import c.universe._
    val paramRepTree = c.Expr[String](Literal(Constant(s)))
    reify {
      LogArg(Seq(paramRepTree.splice), param.splice, hiddenName = true, findCodec(param).splice)
    }
  }

  private def reifiedExtracted(param: c.Expr[Any], s: Seq[String]): c.universe.Expr[LogArg] = {
    import c.universe._
    val list = c.Expr[Seq[String]](q"List(..$s)")

    reify {
      LogArg(list.splice, param.splice, hiddenName = false, findCodec(param).splice)
    }
  }

  private def findCodec(param: c.Expr[Any]): c.Expr[Option[LogstageCodec[Any]]] = {
    import c.universe._
    val maybeCodec = scala.util.Try(c.inferImplicitValue(appliedType(weakTypeOf[LogstageCodec[Nothing]].typeConstructor, param.tree.tpe), silent = false))
    debug(param.tree, s"Logstage codec for argument $param: ${param.tree.tpe} == $maybeCodec")

    val tc = maybeCodec match {
      case Failure(exception) if strict =>
        c.error(param.tree.pos, s"Implicit search failed for parameter ${c.universe.showCode(param.tree)}: ${param.tree.tpe}")
        throw exception
      case Failure(_) =>
        None
      case Success(value) =>
        Some(value)
    }

    c.Expr[Option[LogstageCodec[Any]]](q"$tc")
  }
}

