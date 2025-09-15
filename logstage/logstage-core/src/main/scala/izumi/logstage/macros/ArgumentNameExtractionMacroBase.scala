package izumi.logstage.macros

import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.LogstageCodec
import izumi.logstage.macros.ExtractedName.{NChar, NString}

abstract class ArgumentNameExtractionMacroBase {
  type Tree
  type Expr[+A]

  protected def debug(arg: Tree, s: => String): Unit
  protected def warning(arg: Tree, s: String): Unit
  protected def abort(arg: Tree, s: String): Nothing

  protected def showRaw(tree: Tree): String
  protected def showCode(tree: Tree): String

  protected def flattenExprs(expressions: Seq[Expr[LogArg]]): Expr[List[LogArg]]

  protected def ArrowArg_unapply(arg: Tree): Option[(Tree, ExtractedName)]
  protected def HiddenArrowArg_unapply(arg: Tree): Option[(Tree, ExtractedName)]
  protected def NameSeq_unapply(arg: Tree): Option[Seq[String]]

  protected def LiteralConstant_unapply(arg: Tree): Option[Any]
  protected def mkStringConstant(arg: String): Tree

  protected object ArrowArg {
    def unapply(arg: Tree): Option[(Tree, ExtractedName)] = ArrowArg_unapply(arg)
  }
  protected object HiddenArrowArg {
    def unapply(arg: Tree): Option[(Tree, ExtractedName)] = HiddenArrowArg_unapply(arg)
  }
  protected object NameSeq {
    def unapply(arg: Tree): Option[Seq[String]] = NameSeq_unapply(arg)
  }
  protected object LiteralConstant {
    def unapply(arg: Tree): Option[Any] = LiteralConstant_unapply(arg)
  }

  protected def reifiedPrefixedValue(param: Tree, value: Tree, prefix: String): Expr[LogArg]

  protected def reifiedExtracted(param: Tree, s: Seq[String], hidden: Boolean): Expr[LogArg]

  protected def findCodec(param: Tree, strict: Boolean): Expr[Option[LogstageCodec[Any]]]

  val example: String =
    s"""1) Simple variable:
       |   logger.info(s"My message: $$argument")
       |2) Chain:
       |   logger.info(s"My message: $${call.method} $${access.value}")
       |3) Named expression:
       |   logger.info(s"My message: $${Some.expression -> "argname"}")
       |4) Named expression, hidden name:
       |   logger.info(s"My message: $${Some.expression -> "argname" -> null}")
       |5) Anonymous expression, hidden name:
       |   logger.info(s"My message: $${Some.expression -> null}")
       |6) De-camelcased name:
       |   logger.info($${camelCaseName -> ' '})
       |""".stripMargin

  private[macros] def recoverArgNames(args: Seq[Tree]): Expr[List[LogArg]] = {
    val expressions = args.map(recoverArgName)

    flattenExprs(expressions)
  }

  private[macros] def recoverArgName(arg0: Tree): Expr[LogArg] = {
    arg0 match {
      case param @ NameSeq(seq) =>
        reifiedExtracted(param, seq, hidden = false)

      case param @ ArrowArg(tree, name) => // ${x -> "name"}
        val extracted = fromExtractedName(param, tree, name)
        reifiedExtracted(tree, extracted, hidden = false)

      case param @ HiddenArrowArg(tree, name) => // ${x -> "name" -> null }
        val extracted = fromExtractedName(param, tree, name)
        reifiedExtracted(tree, extracted, hidden = true)

      case param @ LiteralConstant(v) => // ${2+2}
        warning(
          param,
          s"""Constant expression as a logger argument: $v, this makes no sense.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |$example
             |""".stripMargin,
        )

        reifiedPrefixedValue(param, param, "UNNAMED")

      case v =>
        warning(
          v,
          s"""Expression as a logger argument: $v
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |$example
             |
             |Tree: ${showRaw(v)}
             |""".stripMargin,
        )
        reifiedPrefixedValue(mkStringConstant(showCode(v)), v, "EXPRESSION")
    }
  }

  private def fromExtractedName(param: Tree, tree: Tree, name: ExtractedName): Seq[String] = {
    name match {
      case NChar(ch) if ch == ' ' =>
        tree match {
          case NameSeq(seq) =>
            seq.map(_.camelToUnderscores.replace('_', ' '))
          case _ =>
            Seq(ch.toString)
        }
      case NChar(ch) =>
        abort(
          param,
          s"""Unsupported mapping: $ch
             |
             |You have the following ways to assign a name:
             |$example
             |""".stripMargin,
        )

      case NString(s) =>
        Seq(s)
    }
  }

}

object ArgumentNameExtractionMacroBase {
  private[macros] final val applyDebug = DebugProperties.`izumi.debug.macro.logstage`.boolValue(false)
}
