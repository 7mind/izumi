package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry, LoggerId}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  def receiver: LogRouter
  def customContext: CustomContext

  @inline final def log(entry: Log.Entry): Unit = {
    if (receiver.acceptable(entry.context.static.id, entry.context.dynamic.level)) {
      unsafeLog(entry)
    }
  }

  @inline final def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): Unit = {
    if (receiver.acceptable(LoggerId(pos.get.applicationPointId), logLevel)) {
      unsafeLog(Log.Entry(logLevel, messageThunk)(pos))
    }
  }

  /** Log irrespective of minimum log level */
  @inline private[this] final def unsafeLog(entry: Log.Entry): Unit = {
    val ctx = customContext
    val entryWithCtx = addCustomCtx(entry, ctx)
    receiver.log(entryWithCtx)
  }

  @inline private[this] def addCustomCtx(entry: Log.Entry, ctx: CustomContext): Entry = {
    if (ctx.values.isEmpty) {
      entry
    } else {
      entry.copy(context = entry.context.copy(customContext = entry.context.customContext + ctx))
    }
  }

}
