package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.AbstractLogger
import com.github.pshirshov.izumi.logstage.api.Log._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait LoggingMacro {
  self: AbstractLogger =>

  def receiver: LogRouter

  def customContext: Log.CustomContext

  def log(entry: Log.Entry): Unit = {
    val ctx = customContext

    val entryWithCtx = if (ctx.values.isEmpty) {
      entry
    } else {
      entry.copy(context = entry.context.copy(customContext = entry.context.customContext + ctx))
    }

    receiver.log(entryWithCtx)
  }

  final def log(logLevel: Log.Level, message: Message)(implicit pos: CodePositionMaterializer): Unit = {
    log(Log.Entry(logLevel, message)(pos))
  }

  final def trace(message: Message): Unit = log(Log.Level.Trace, message)
  final def debug(message: Message): Unit = log(Log.Level.Debug, message)
  final def info(message: Message): Unit = log(Log.Level.Info, message)
  final def warn(message: Message): Unit = log(Log.Level.Warn, message)
  final def error(message: Message): Unit = log(Log.Level.Error, message)
  final def crit(message: Message): Unit = log(Log.Level.Crit, message)

  //  final def trace(message: String): Unit = macro scTraceMacro
  //  final def debug(message: String): Unit = macro scDebugMacro
  //  final def info(message: String): Unit = macro scInfoMacro
  //  final def warn(message: String): Unit = macro scWarnMacro
  //  final def error(message: String): Unit = macro scErrorMacro
  //  final def crit(message: String): Unit = macro scCritMacro
}

object LoggingMacro {
//  def scTraceMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Trace))
//  }
//
//  def scDebugMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Debug))
//  }
//
//  def scInfoMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Info))
//  }
//
//  def scWarnMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Warn))
//  }
//
//  def scErrorMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Error))
//  }
//
//  def scCritMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
//    import c.universe._
//    stringContextSupportMacro(c)(message, reify(Log.Level.Crit))
//  }

  def logMessageMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Message] = {
    mkLogMessage(c)(message)
  }

//  private def stringContextSupportMacro(c: blackbox.Context)(message: c.Expr[String], logLevel: c.Expr[Log.Level]): c.Expr[Unit] = {
//    val messageTree = mkLogMessage(c)(message)
//    logMacro(c)(messageTree, logLevel)
//  }

  private def reifyContext(c: blackbox.Context)(stringContext: c.universe.Tree, namedArgs: c.Expr[List[LogArg]]): c.Expr[Message] = {
    import c.universe._
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice
        , namedArgs.splice
      )
    }
  }
//
//  private def logMacro(c: blackbox.Context)(message: c.Expr[Message], logLevel: c.Expr[Log.Level]): c.Expr[Unit] = {
//    import c.universe._
//
//      def mkLogEntry(c: blackbox.Context)(logLevel: c.Expr[Level])(message: c.Expr[Message])(pos: c.Expr[CodePositionMaterializer]): c.Expr[Entry] = {
//        c.universe.reify {
//          Log.Entry(logLevel.splice, message.splice)(pos.splice)
//        }
//      }
//
//    val pos = mkPos(c)
//    val entry = mkLogEntry(c)(logLevel)(message)(pos)
//    val logger = reify(c.prefix.splice.asInstanceOf[LoggingMacro])
//
//    c.Expr[Unit] {
//      q"""{
//            $logger.log($entry)
//          }"""
//    }
//  }

  private def mkLogMessage(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Message] = {
    import c.universe._

    message.tree match {
      // qq causes a weird warning here
      //case q"scala.StringContext.apply($stringContext).s(..$args)" =>
      case Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]) =>
        val namedArgs = ArgumentNameExtractionMacro.recoverArgNames(c)(args.map(p => c.Expr(p)))
        reifyContext(c)(stringContext, namedArgs)

      case Literal(c.universe.Constant(s)) =>
        val emptyArgs = reify(List(LogArg(Seq("@type"), "const", hidden = false)))
        val sc = q"StringContext(${s.toString})"
        reifyContext(c)(sc, emptyArgs)

      case other =>
        c.warning(other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()}.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |1) Simple variable: logger.log(s"My message: $$argument")
             |2) Chain: logger.log(s"My message: $${call.method} $${access.value}")
             |3) Named expression: logger.log(s"My message: $${Some.expression -> "argname"}")
             |4) Hidden arg expression: logger.log(s"My message: $${Some.expression -> "argname" -> null}")
             |""".stripMargin)

        val emptyArgs = reify {
          val repr = c.Expr[String](Literal(Constant(c.universe.showCode(other)))).splice
          List(LogArg(Seq("@type"), "expr", hidden = false), LogArg(Seq("@expr"), repr, hidden = false))
        }
        val sc = q"StringContext($other)"
        reifyContext(c)(sc, emptyArgs)
    }
  }

//  private def mkPos(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
//    CodePositionMaterializer.getEnclosingPosition(c)
//  }

}



