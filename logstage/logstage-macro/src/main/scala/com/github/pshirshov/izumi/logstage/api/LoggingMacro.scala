package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.LoggingMacro.debugMacro
import com.github.pshirshov.izumi.logstage.model.Log.{Message, StaticExtendedContext, ThreadData}
import com.github.pshirshov.izumi.logstage.model.{AbstractLogger, Log, LogReceiver}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox


trait LoggingMacro {
  self: AbstractLogger =>
  
  def receiver: LogReceiver

  def contextStatic: Log.StaticContext

  def contextCustom: Log.CustomContext

  def debug(message: Message): Unit = macro debugMacro
}

object LoggingMacro {
  def debugMacro(c: blackbox.Context)(message: c.Expr[Message]): c.Expr[Unit] = {
    import c.universe._
    logMacroMacro(c)(message, c.Expr[Log.Level](q"Log.Level.Debug"))
  }

  def logMacroMacro(c: blackbox.Context)(message: c.Expr[Message], logLevel: c.Expr[Log.Level]): c.Expr[Unit] = {
    import c.universe._

    val receiver = reify(c.prefix.splice.asInstanceOf[LoggingMacro].receiver)

    val line = c.Expr[Int](c.universe.Literal(Constant(c.enclosingPosition.line)))
    val file = c.Expr[String](c.universe.Literal(Constant(c.enclosingPosition.source.file.name)))

    val context = reify {
      val self = c.prefix.splice.asInstanceOf[LoggingMacro]
      val thread = Thread.currentThread()
      val dynamicContext = Log.DynamicContext(logLevel.splice, ThreadData(thread.getName, thread.getId))

      val staticContext = StaticExtendedContext(self.contextStatic, file.splice, line.splice)
      Log.Context(staticContext, dynamicContext, self.contextCustom)
    }

    c.Expr[Unit] {
      q"""{
            if ($logLevel >= $receiver.level) {
              $receiver.log($context, $message)
            }
          }"""
    }
  }


}



