package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil
import com.github.pshirshov.izumi.logstage.api.Log.{LoggerId, Message, StaticExtendedContext, ThreadData}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

import scala.language.experimental.macros
import scala.reflect.macros.blackbox


trait LoggingMacro {
  self: AbstractLogger =>

  import com.github.pshirshov.izumi.logstage.api.LoggingMacro._

  def receiver: LogRouter

  def contextCustom: Log.CustomContext

  final def trace(message: String): Unit = macro scTraceMacro
  final def debug(message: String): Unit = macro scDebugMacro
  final def info(message: String): Unit = macro scInfoMacro
  final def warn(message: String): Unit = macro scWarnMacro
  final def error(message: String): Unit = macro scErrorMacro
  final def crit(message: String): Unit = macro scCritMacro
}

object LoggingMacro {
  def scTraceMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Trace))
  }

  def scDebugMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Debug))
  }

  def scInfoMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Info))
  }

  def scWarnMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Warn))
  }

  def scErrorMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Error))
  }

  def scCritMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._
    stringContextSupportMacro(c)(message, reify(Log.Level.Crit))
  }

  private def stringContextSupportMacro(c: blackbox.Context)(message: c.Expr[String], logLevel: c.Expr[Log.Level]): c.Expr[Unit] = {
    import c.universe._

    val messageTree = message.tree match {
      // qq causes a weird warning here
      //case q"StringContext($stringContext).s(..$args)" =>
      case c.universe.Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]) =>
        val namedArgs = ArgumentNameExtractionMacro.recoverArgNames(c)(args.map(p => c.Expr(p)))
        reifyContext(c)(stringContext, namedArgs)

      case c.universe.Literal(c.universe.Constant(s)) =>
        val emptyArgs = reify(List("@type" -> "const"))
        val sc = q"StringContext(${s.toString})"
        reifyContext(c)(sc, emptyArgs)

      case other =>
        c.warning(c.enclosingPosition,
          s"""Complex expression as an input for a logger: ${other.toString()}. Variables won't be bound in context. This may not be what you want, use string interpolation instead: s"message with an $${argument}" """)
        val emptyArgs = q"""List("@type" -> "expr", "@expr" -> ${other.toString()})"""
        val sc = q"StringContext($other)"
        reifyContext(c)(sc, c.Expr(emptyArgs))
    }

    logMacro(c)(messageTree, logLevel)
  }

  private def reifyContext(c: blackbox.Context)(stringContext: c.universe.Tree, namedArgs: c.Expr[List[(String, Any)]]) = {
    import c.universe._
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice
        , namedArgs.splice
      )
    }
  }

  private def logMacro(c: blackbox.Context)(message: c.Expr[Message], logLevel: c.Expr[Log.Level]): c.Expr[Unit] = {
    import c.universe._

    val receiver = reify(c.prefix.splice.asInstanceOf[LoggingMacro].receiver)

    val (line, file, applicationPointId) = MacroUtil.EnclosingPosition.getEnclosingPositionExprs(c)

    val loggerId = reify {
      LoggerId(applicationPointId.splice)
    }

    val entry = reify {
      val self = c.prefix.splice.asInstanceOf[LoggingMacro]
      val thread = Thread.currentThread()
      val dynamicContext = Log.DynamicContext(logLevel.splice, ThreadData(thread.getName, thread.getId), System.currentTimeMillis())

      val extendedStaticContext = StaticExtendedContext(loggerId.splice, file.splice, line.splice)
      Log.Entry(message.splice, Log.Context(extendedStaticContext, dynamicContext, self.contextCustom))
    }

    c.Expr[Unit] {
      q"""{
            if ($receiver.acceptable($loggerId, $logLevel)) {
              $receiver.log($entry)
            }
          }"""
    }
  }

  //  def debugMacro(c: blackbox.Context)(message: c.Expr[Message]): c.Expr[Unit] = {
  //    import c.universe._
  //    logMacro(c)(message, c.Expr[Log.Level](q"Log.Level.Debug"))
  //  }
  //
}



