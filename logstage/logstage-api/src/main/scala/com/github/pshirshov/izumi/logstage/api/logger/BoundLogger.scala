package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.api.ArgumentNameExtractionMacro
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.Log.{CustomContext, EmptyCustomContext, Message}

import scala.language.implicitConversions




class BoundLogger(logger: RoutingLogReceiver)(implicit val context: WithLogContext) {

  def debug(message: => Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Debug)(custom)
  }

  def warn(message: => Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Warn)(custom)
  }

  def info(message: => Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Info)(custom)
  }

  def error(message: => Message)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    withLogLevel(message, Log.Level.Error)(custom)
  }

  private def withLogLevel(message: => Message, lvl: Log.Level)(implicit custom: CustomContext = EmptyCustomContext): Unit = {
    if (logger.level > lvl) {
      //logger.log(Context(context.context, DynamicContext(lvl, context.thread), custom), message)
    }
  }

  import ArgumentNameExtractionMacro._
  implicit def interpolator(sc: StringContext): LogSC = new LogSC(sc)

}

object BoundLogger {
  import ArgumentNameExtractionMacro._

  implicit class ArgumentExtractorPassthrough(val sc: StringContext) {
    def m(args: Any*): Message = new LogSC(sc).m(args)
  }
}
