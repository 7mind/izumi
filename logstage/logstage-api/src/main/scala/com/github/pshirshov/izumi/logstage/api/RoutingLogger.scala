package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

/** Logger that forwards entries to [[LogRouter]] */
trait RoutingLogger extends AbstractLogger {

  protected def router: LogRouter
  protected def customContext: CustomContext

  @inline override final def acceptable(loggerId: Log.LoggerId, logLevel: Log.Level): Boolean = {
    router.acceptable(loggerId, logLevel)
  }

  /** Log irrespective of minimum log level */
  @inline override final def unsafeLog(entry: Log.Entry): Unit = {
    val entryWithCtx = entry.addCustomContext(customContext)
    router.log(entryWithCtx)
  }

}
