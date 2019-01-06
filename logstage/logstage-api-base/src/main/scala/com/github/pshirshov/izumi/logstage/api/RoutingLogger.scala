package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{CustomContext, Entry}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  def receiver: LogRouter
  def customContext: CustomContext

  @inline override final def log(entry: Log.Entry): Unit = {
    val ctx = customContext
    val entryWithCtx = addCustomCtx(entry, ctx)
    receiver.log(entryWithCtx)
  }

  @inline override final def log(logLevel: Log.Level)(message: Log.Message)(implicit pos: CodePositionMaterializer): Unit = {
    log(Log.Entry(logLevel, message)(pos))
  }

  @inline private[this] def addCustomCtx(entry: Log.Entry, ctx: CustomContext): Entry = {
    if (ctx.values.isEmpty) {
      entry
    } else {
      entry.copy(context = entry.context.copy(customContext = entry.context.customContext + ctx))
    }
  }

}
