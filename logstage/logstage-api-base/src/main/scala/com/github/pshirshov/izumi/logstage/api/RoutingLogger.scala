package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry, LoggerId}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  def receiver: LogRouter
  def customContext: CustomContext

  @inline override final def acceptable(loggerId: LoggerId, logLevel: Log.Level): Boolean = {
    receiver.acceptable(loggerId, logLevel)
  }

  @inline override final def unsafeLog(entry: Log.Entry): Unit = {
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
