package org.bitbucket.pshirshov.izumi.example

import org.bitbucket.pshirshov.izumi.InterpolatorMacros
import org.bitbucket.pshirshov.izumi.logger._
import org.bitbucket.pshirshov.izumi.logger.api.Logger


// Usage

trait Logging extends WithLogContext {
  val logger = new BoundLogger(Logging.logging)(this)
}

object Logging {
  val logging = new Logger {
    override protected def logConfigService: LogConfigService = new LogConfigService {
      val logFilter = new LogFilter {}
      val sink = new LogSink {}
      val mapping = new LogMapping(filter = logFilter, sink)

      override def config(e: Log.Entry): Seq[LogMapping] = Seq(mapping)
    }
  }
}


object LoggedApp extends App with Logging with InterpolatorMacros {

  implicit def customLoggingContext: Log.CustomContext = new Log.CustomContext {
    override def values = Map("userId" -> "c6b272ae-0206-11e8-ba89-0ed5f89f718b")
  }

  val userId = "c6b272ae-0206-11e8-ba89-0ed5f89f718b"

  val amount = 4

  logger.debug(l"should send to ${userId} ${amount} within ${5} minutes")
//  logger.info(l"should send to ${userId} ${amount} within ${5} minutes")
//  logger.warn(l"should send to ${userId} ${amount} within ${5} minutes")

//  try {
//    val a = null
//    a.getClass
//  } catch {
//    case f =>
//      logger.error(l"dunno how to handle exception =${f.getMessage}")
//  }


}
