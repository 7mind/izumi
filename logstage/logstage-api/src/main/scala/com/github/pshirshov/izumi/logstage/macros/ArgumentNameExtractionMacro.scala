package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.logstage.api.Log.LogArg

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object ArgumentNameExtractionMacro {
  protected[macros] def recoverArgNames(c: blackbox.Context)(args: Seq[c.Expr[Any]]): c.Expr[List[LogArg]] = {
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
      def unapply(arg: c.universe.Tree): Option[(c.Expr[Any], String)] = {
        arg match {
          case ArrowPair(expr, Literal(Constant(name: String))) => // ${value -> "name"}
            Some((c.Expr(expr), name))
          case ArrowPair(expr@NameSeq(names), Literal(Constant(null))) => // ${value -> null}
            Some((c.Expr(expr), names.last))
          case _ =>
            None
        }
      }
    }

    object HiddenArrowArg {
      def unapply(arg: c.universe.Tree): Option[(c.Expr[Any], String)] = {
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

      val debug = false

      def debug(arg: c.universe.Tree, s: => String): Unit = {
        if (debug) {
          c.warning(arg.pos, s)
        }
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

    val expressions = args.map {
      param =>
        param.tree match {
          case NameSeq(seq) =>
            reifiedExtracted(c)(param, seq)

          case ArrowArg(expr, name) => // ${x -> "name"}
            reifiedExtracted(c)(expr, Seq(name))

          case HiddenArrowArg(expr, name) => // ${x -> "name" -> null }
            reifiedExtractedHidden(c)(expr, name)

          case t@c.universe.Literal(c.universe.Constant(v)) => // ${2+2}
            c.warning(t.pos,
              s"""Constant expression as a logger argument: $v, this makes no sense.""".stripMargin)

            reifiedPrefixed(c)(param, "UNNAMED")

          case v =>
            c.warning(v.pos,
              s"""Expression as a logger argument: $v
                 |
                 |But Logstage expects you to use string interpolations instead, such as:
                 |1) Simple variable: logger.log(s"My message: $$argument")
                 |2) Chain: logger.log(s"My message: $${call.method} $${access.value}")
                 |3) Named expression: logger.log(s"My message: $${Some.expression -> "argname"}")
                 |4) Hidden arg expression: logger.log(s"My message: $${Some.expression -> "argname" -> null}")
                 |
                 |Tree: ${c.universe.showRaw(v)}
               """.stripMargin)
            reifiedPrefixedValue(c)(c.Expr[String](Literal(Constant(c.universe.showCode(v)))), param, "EXPRESSION")
        }
    }

    c.Expr[List[LogArg]] {
      q"List(..$expressions)"
    }
  }

  private def reifiedPrefixed(c: blackbox.Context)(param: c.Expr[Any], prefix: String): c.universe.Expr[LogArg] = {
    reifiedPrefixedValue(c)(param, param, prefix)
  }

  private def reifiedPrefixedValue(c: blackbox.Context)(param: c.Expr[Any], value: c.Expr[Any], prefix: String): c.universe.Expr[LogArg] = {
    import c.universe._
    val prefixRepr = c.Expr[String](Literal(Constant(prefix)))
    reify {
      LogArg(Seq(s"${prefixRepr.splice}:${param.splice}"), value.splice, hiddenName = false)
    }
  }


  private def reifiedExtractedHidden(c: blackbox.Context)(param: c.Expr[Any], s: String): c.universe.Expr[LogArg] = {
    import c.universe._
    val paramRepTree = c.Expr[String](Literal(Constant(s)))
    reify {
      LogArg(Seq(paramRepTree.splice), param.splice, hiddenName = true)
    }
  }

  private def reifiedExtracted(c: blackbox.Context)(param: c.Expr[Any], s: Seq[String]): c.universe.Expr[LogArg] = {
    import c.universe._
    val list = c.Expr[Seq[String]](q"List(..$s)")
    reify {
      LogArg(list.splice, param.splice, hiddenName = false)
    }
  }

}

