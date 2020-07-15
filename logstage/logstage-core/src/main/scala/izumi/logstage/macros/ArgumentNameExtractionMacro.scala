package izumi.logstage.macros

import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.LogstageCodec

import scala.annotation.tailrec
import scala.reflect.macros.blackbox
import scala.util.{Failure, Success}

class ArgumentNameExtractionMacro[C <: blackbox.Context](final val c: C, strict: Boolean) {
  private[this] final val applyDebug = DebugProperties.`izumi.debug.macro.logstage`.asBoolean(false)

  import ExtractedName._
  import c.universe._

  @inline private[this] final def debug(arg: Tree, s: => String): Unit = {
    if (applyDebug) {
      c.warning(arg.pos, s)
    }
  }

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

  private[macros] def recoverArgNames(args: Seq[Tree]): c.Expr[List[LogArg]] = {
    object Arrow {
      def unapply(arg: Tree): Option[TypeApply] = {
        arg match {
          case t @ TypeApply(Select(Select(Ident(TermName("scala")), _), TermName("ArrowAssoc")), List(TypeTree())) => Some(t)
          case _ => None
        }
      }
    }

    object ArrowPair {
      def unapply(arg: Tree): Option[(Tree, Tree)] = {
        arg match {
          case Apply(
                TypeApply(
                  Select(
                    Apply(Arrow(_), leftExpr :: Nil),
                    _, /*TermName("$minus$greater")*/
                  ),
                  List(TypeTree()),
                ),
                List(rightExpr),
              ) =>
            Some((leftExpr, rightExpr))
          case _ => None
        }
      }
    }

    object ArrowArg {
      def unapply(arg: Tree): Option[(Tree, ExtractedName)] = {
        arg match {
          case ArrowPair(expr, Literal(Constant(char: Char))) => // ${value -> ' '}
            Some((expr, NChar(char)))
          case ArrowPair(expr, Literal(Constant(name: String))) => // ${value -> "name"}
            Some((expr, NString(name)))
          case ArrowPair(expr @ NameSeq(names), Literal(Constant(null))) => // ${value -> null}
            Some((expr, NString(names.last)))
          case _ =>
            None
        }
      }
    }

    object HiddenArrowArg {
      def unapply(arg: Tree): Option[(Tree, ExtractedName)] = {
        arg match {
          case ArrowPair(expr, Literal(Constant(null))) => // ${value -> "name" -> null}
            ArrowArg.unapply(expr)
          case _ =>
            None
        }
      }
    }

    object NameSeq {
      def unapply(arg: Tree): Option[Seq[String]] = {
        extract(arg, Seq.empty)
      }

      @tailrec
      private[this] def extract(arg: Tree, acc: Seq[String]): Option[Seq[String]] = {
        arg match {
          case Select(Ident(TermName("scala")), TermName("Predef")) =>
            debug(arg, s"END-PREDEF")
            Some(acc)

          case Select(e, TermName(s)) => // ${x.value}
            debug(arg, s"B1: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case Apply(Select(e, TermName(s)), List()) => // ${x.getSomething}
            debug(arg, s"B2: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case Apply(Select(e, _), Ident(TermName(s)) :: Nil) => // ${Predef.ops(x).getSomething}
            debug(arg, s"B2-1: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
            extract(e, s +: acc)

          case This(TypeName(s)) =>
            debug(arg, s"END-THIS: arg=${showRaw(arg)} s='$s', acc=$acc")
            if (s.isEmpty) {
              Some("this" +: acc)
            } else {
              Some(s +: acc)
            }

          case Ident(TermName(s)) =>
            debug(arg, s"END-NAME: arg=${showRaw(arg)} s='$s', acc=$acc")
            Some(s +: acc)

          case _ =>
            debug(arg, s"END-NONE, arg=${showRaw(arg)}, acc=$acc")
            None
        }
      }
    }

    val expressions = args.map {
      case param @ NameSeq(seq) =>
        reifiedExtracted(param, seq)

      case param @ ArrowArg(tree, name) => // ${x -> "name"}
        name match {
          case NChar(ch) if ch == ' ' =>
            tree match {
              case NameSeq(seq) =>
                reifiedExtracted(tree, seq.map(_.camelToUnderscores.replace('_', ' ')))
              case _ =>
                reifiedExtracted(tree, Seq(ch.toString))
            }
          case NChar(ch) =>
            c.abort(
              param.pos,
              s"""Unsupported mapping: $ch
                 |
                 |You have the following ways to assign a name:
                 |$example
                 |""".stripMargin,
            )

          case NString(s) =>
            reifiedExtracted(tree, Seq(s))
        }

      case HiddenArrowArg(tree, name) => // ${x -> "name" -> null }
        reifiedExtractedHidden(tree, name.str)

      case param @ (t @ Literal(Constant(v))) => // ${2+2}
        c.warning(
          t.pos,
          s"""Constant expression as a logger argument: $v, this makes no sense.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |$example
             |""".stripMargin,
        )

        reifiedPrefixedValue(param, param, "UNNAMED")

      case v =>
        c.warning(
          v.pos,
          s"""Expression as a logger argument: $v
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |$example
             |
             |Tree: ${showRaw(v)}
             |""".stripMargin,
        )
        reifiedPrefixedValue(Literal(Constant(showCode(v))), v, "EXPRESSION")
    }

    c.Expr[List[LogArg]] {
      q"_root_.scala.collection.immutable.List(..$expressions)"
    }
  }
  private[this] def reifiedPrefixedValue(param: Tree, value: Tree, prefix: String): Expr[LogArg] = {
    val prefixRepr = c.Expr[String](Literal(Constant(prefix)))
    val paramExpr = c.Expr[Any](param)
    val valueExpr = c.Expr[Any](value)
    reify {
      LogArg(Seq(s"${prefixRepr.splice}:${paramExpr.splice}"), valueExpr.splice, hiddenName = false, findCodec(value).splice)
    }
  }

  private[this] def reifiedExtractedHidden(param: Tree, s: String): Expr[LogArg] = {
    val paramRepTree = c.Expr[String](Literal(Constant(s)))
    val expr = c.Expr[Any](param)
    reify {
      LogArg(Seq(paramRepTree.splice), expr.splice, hiddenName = true, findCodec(param).splice)
    }
  }

  private[this] def reifiedExtracted(param: Tree, s: Seq[String]): Expr[LogArg] = {
    val list = c.Expr[Seq[String]](q"List(..$s)")
    val expr = c.Expr[Any](param)
    reify {
      LogArg(list.splice, expr.splice, hiddenName = false, findCodec(param).splice)
    }
  }

  private[this] def findCodec(param: Tree, tpe: Type): Expr[Option[LogstageCodec[Any]]] = {
    val maybeCodec = scala.util.Try(c.inferImplicitValue(appliedType(weakTypeOf[LogstageCodec[Nothing]].typeConstructor, tpe), silent = false))
    debug(param, s"Logstage codec for argument $param of type `$tpe` == $maybeCodec")

    val tc = maybeCodec match {
      case Failure(_) if strict =>
        c.abort(param.pos, s"Implicit search failed for `logstage.LogstageCodec[$tpe]` for parameter `${showCode(param)}` of type `$tpe`")
      case Failure(_) =>
        None
      case Success(value) =>
        Some(value)
    }

    c.Expr[Option[LogstageCodec[Any]]](q"$tc")
  }
  @inline private[this] def findCodec(param: Tree): Expr[Option[LogstageCodec[Any]]] = findCodec(param, param.tpe)
}
