package izumi.logstage.macros

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.LogstageCodec
import izumi.logstage.macros.ArgumentNameExtractionMacroBase.applyDebug
import izumi.logstage.macros.ExtractedName.{NChar, NString}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox
import scala.util.{Failure, Success}

class ArgumentNameExtractionMacro[C <: blackbox.Context](final val c: C, strict: Boolean) extends ArgumentNameExtractionMacroBase {
  override type Expr[+A] = c.Expr[A]
  override type Tree = c.universe.Tree

  import c.universe.*

  @inline override protected final def debug(arg: Tree, s: => String): Unit = {
    if (applyDebug) {
      c.warning(arg.pos, s)
    }
  }
  override protected def abort(arg: Tree, s: String): Nothing = {
    c.abort(arg.pos, s)
  }
  override protected def warning(arg: Tree, s: String): Unit = {
    c.warning(arg.pos, s)
  }

  override protected def flattenExprs(expressions: Seq[c.Expr[LogArg]]): c.Expr[List[LogArg]] = {
    c.Expr[List[LogArg]] {
      q"_root_.scala.collection.immutable.List(..$expressions)"
    }
  }

  override protected def LiteralConstant_unapply(arg: Tree): Option[Any] = {
    arg match {
      case Literal(Constant(a)) =>
        Some(a)
      case _ =>
        None
    }
  }
  override protected def mkStringConstant(arg: String): Tree = Literal(Constant(arg))

  override protected def showCode(tree: Tree): String = c.universe.showCode(tree)
  override protected def showRaw(tree: c.universe.Tree): String = c.universe.showRaw(tree)

  private object Arrow {
    def unapply(arg: Tree): Option[Tree] = {
      arg match {
        case t @ TypeApply(Select(Select(Ident(TermName("scala")), _), TermName("ArrowAssoc")), List(TypeTree())) => Some(t)
        case _ => None
      }
    }
  }

  private object ArrowPair {
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

  override protected def ArrowArg_unapply(arg: Tree): Option[(Tree, ExtractedName)] = {
    arg match {
      case ArrowPair(expr, Literal(Constant(char: Char))) => // ${value -> ' '}
        Some((expr, NChar(char)))
      case ArrowPair(expr, Literal(Constant(name: String))) => // ${value -> "name"}
        Some((expr, NString(name)))
      case _ =>
        None
    }
  }

  override protected def HiddenArrowArg_unapply(arg: Tree): Option[(Tree, ExtractedName)] = {
    arg match {
      case ArrowPair(expr @ NameSeq(names), Literal(Constant(null))) => // ${value -> null}
        Some((expr, NString(names.last)))
      case ArrowPair(expr, Literal(Constant(null))) => // ${value -> "name" -> null}
        ArrowArg.unapply(expr)
      case _ =>
        None
    }
  }

  override protected def NameSeq_unapply(arg: Tree): Option[Seq[String]] = {
    @tailrec def extract(arg: Tree, acc: Seq[String]): Option[Seq[String]] = {
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

    extract(arg, Seq.empty)
  }

  override protected def reifiedPrefixedValue(param: Tree, value: Tree, prefix: String): Expr[LogArg] = {
    val prefixRepr = c.Expr[String](Literal(Constant(prefix)))
    val paramExpr = c.Expr[Any](param)
    val valueExpr = c.Expr[Any](value)
    reify {
      LogArg(Seq(s"${prefixRepr.splice}:${paramExpr.splice}"), valueExpr.splice, hiddenName = false, findCodec(value, strict).splice)
    }
  }

  override protected def reifiedExtracted(param: Tree, s: Seq[String], hidden: Boolean): Expr[LogArg] = {
    val list = c.Expr[Seq[String]](q"List(..$s)")
    val expr = c.Expr[Any](param)
    val hiddenExpr = c.Expr[Boolean](Literal(Constant(hidden)))
    reify {
      LogArg(list.splice, expr.splice, hiddenName = hiddenExpr.splice, findCodec(param, strict).splice)
    }
  }

  override protected def findCodec(param: Tree, strict: Boolean): Expr[Option[LogstageCodec[Any]]] = {
    val tpe = param.tpe
    val maybeCodec = scala.util.Try(c.inferImplicitValue(appliedType(weakTypeOf[LogstageCodec[Nothing]].typeConstructor, tpe), silent = false))
    debug(param, s"Logstage codec for argument $param of type `$tpe` == $maybeCodec")

    val tc = maybeCodec match {
      case Failure(_) if strict =>
        c.abort(param.pos, s"Implicit search failed for `logstage.LogstageCodec[$tpe]` for parameter `${showCode(param)}` of type `$tpe`. LogstageCodec instances are required in Strict mode")
      case Failure(_) =>
        None
      case Success(value) =>
        Some(value)
    }

    c.Expr[Option[LogstageCodec[Any]]](q"$tc")
  }

}
