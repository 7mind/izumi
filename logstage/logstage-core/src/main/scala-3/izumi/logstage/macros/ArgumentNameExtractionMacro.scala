package izumi.logstage.macros

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.LogstageCodec
import izumi.logstage.macros.ArgumentNameExtractionMacroBase.applyDebug
import izumi.logstage.macros.ExtractedName.{NChar, NString}

import scala.annotation.tailrec
import scala.quoted.{Expr, Quotes, Type}

class ArgumentNameExtractionMacro[Q <: Quotes](strict: Boolean)(using final val qctx: Q) extends ArgumentNameExtractionMacroBase {
  override type Expr[+A] = scala.quoted.Expr[A]
  override type Tree = qctx.reflect.Term

  import qctx.reflect.*

  private val arrowAssocSym: Symbol = Symbol.requiredMethod("scala.Predef.ArrowAssoc")

  @inline override protected final def debug(arg: Term, s: => String): Unit = {
    if (applyDebug) {
      qctx.reflect.report.warning(s, arg.pos)
    }
  }
  override protected def abort(arg: Term, s: String): Nothing = {
    qctx.reflect.report.errorAndAbort(s, arg.pos)
  }
  override protected def warning(arg: Term, s: String): Unit = {
    qctx.reflect.report.warning(s, arg.pos)
  }

  override protected def flattenExprs(expressions: Seq[Expr[LogArg]]): Expr[List[LogArg]] = {
    Expr.ofList[LogArg](expressions)
  }

  override protected def LiteralConstant_unapply(arg: Term): Option[Any] = {
    arg match {
      case Literal(c: Constant) =>
        Some(c.value)
      case _ =>
        None
    }
  }
  override protected def mkStringConstant(arg: String): Term = Literal(StringConstant(arg))

  override protected def showCode(tree: Term): String = qctx.reflect.Printer.TreeCode.show(tree)
  override protected def showRaw(tree: Term): String = qctx.reflect.Printer.TreeStructure.show(tree)

  private object Arrow {
    def unapply(arg: Term): Option[Term] = {
      arg match {
        case t @ TypeApply(l, List(_)) if l.symbol == arrowAssocSym => Some(t)
        case _ => None
      }
    }
  }

  private object ArrowPair {
    def unapply(arg: Term): Option[(Term, Term)] = {
      arg match {
        case Apply(
              TypeApply(
                Select(
                  Apply(Arrow(_), leftExpr :: Nil),
                  _, /*TermName("$minus$greater")*/
                ),
                List(_),
              ),
              List(rightExpr),
            ) =>
          Some((leftExpr, rightExpr))
        case _ => None
      }
    }
  }

  override protected def ArrowArg_unapply(arg: Term): Option[(Term, ExtractedName)] = {
    arg match {
      case ArrowPair(expr, Literal(CharConstant(char: Char))) => // ${value -> ' '}
        Some((expr, NChar(char)))
      case ArrowPair(expr, Literal(StringConstant(name: String))) => // ${value -> "name"}
        Some((expr, NString(name)))
      case _ =>
        None
    }
  }

  override protected def HiddenArrowArg_unapply(arg: Term): Option[(Term, ExtractedName)] = {
    arg match {
      case ArrowPair(expr @ NameSeq(names), Literal(NullConstant())) => // ${value -> null}
        Some((expr, NString(names.last)))
      case ArrowPair(expr, Literal(NullConstant())) => // ${value -> "name" -> null}
        ArrowArg.unapply(expr)
      case _ =>
        None
    }
  }

  override protected def NameSeq_unapply(arg: Term): Option[Seq[String]] = {
    @tailrec def extract(arg: Term, acc: Seq[String]): Option[Seq[String]] = {
      arg match {
        case Select(Ident("scala"), "Predef") =>
          debug(arg, s"END-PREDEF")
          Some(acc)

        case Select(e, s) => // ${x.value}
          debug(arg, s"B1: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
          extract(e, s +: acc)

        case Apply(Select(e, s), List()) => // ${x.getSomething}
          debug(arg, s"B2: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
          extract(e, s +: acc)

        case Apply(Select(e, _), Ident(s) :: Nil) => // ${Predef.ops(x).getSomething}
          debug(arg, s"B2-1: arg=${showRaw(arg)} e=${showRaw(e)}, s='$s', acc=$acc")
          extract(e, s +: acc)

        case This(maybeS) =>
          debug(arg, s"END-THIS: arg=${showRaw(arg)} s='$maybeS', acc=$acc")
          if (maybeS.isEmpty) {
            Some("this" +: acc)
          } else {
            Some(maybeS.get +: acc)
          }

        case Ident(s) =>
          debug(arg, s"END-NAME: arg=${showRaw(arg)} s='$s', acc=$acc")
          Some(s +: acc)

        case _ =>
          debug(arg, s"END-NONE, arg=${showRaw(arg)}, acc=$acc")
          None
      }
    }

    extract(arg, Seq.empty)
  }

  override protected def reifiedPrefixedValue(param: Term, value: Term, prefix: String): Expr[LogArg] = {
    val prefixRepr = Expr[String](prefix)
    val paramExpr = param.asExpr
    val valueExpr = value.asExpr
    '{ LogArg(Seq(s"${${ prefixRepr }}:${${ paramExpr }}"), ${ valueExpr }, hiddenName = false, ${ findCodec(value, strict) }) }
  }

  override protected def reifiedExtracted(param: Term, s: Seq[String], hidden: Boolean): Expr[LogArg] = {
    val list = Expr.ofList(s.map(Expr[String](_)))
    val expr = param.asExpr
    val hiddenExpr = Expr[Boolean](hidden)
    '{ LogArg(${ list }, ${ expr }, hiddenName = ${ hiddenExpr }, ${ findCodec(param, strict) }) }
  }

  override protected def findCodec(param: Term, strict: Boolean): Expr[Option[LogstageCodec[Any]]] = {
    val tpe = param.tpe.widenTermRefByName
    val sym = Symbol.requiredClass("izumi.logstage.api.rendering.LogstageCodec")
    val appliedType = AppliedType(sym.typeRef, List(tpe))
    val maybeCodec = Implicits.search(appliedType)
    debug(param, s"Logstage codec for argument $param of type `$tpe` == $maybeCodec")

    maybeCodec match {
      case s: ImplicitSearchSuccess =>
        val c = s.tree.asExpr.asInstanceOf[Expr[LogstageCodec[?]]]
        '{ Some(${ c }.asInstanceOf[LogstageCodec[Any]]) }
      case _ if strict =>
        report.errorAndAbort(
          s"Implicit search failed for `${appliedType.show}` for parameter `${showCode(param)}` of type `$tpe`. LogstageCodec instances are required in Strict mode"
        )
      case _ =>
        Expr(None)
    }
  }

}
