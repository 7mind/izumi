package org.bitbucket.pshirshov.izumi.example

import org.bitbucket.pshirshov.izumi.logger.Log.LogMapping
import org.bitbucket.pshirshov.izumi.logger._
import org.bitbucket.pshirshov.izumi.logger.api.Logger


// Usage
import Logger._

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


object LoggedApp extends App with Logging {

  implicit def customLoggingContext: Log.CustomContext = new Log.CustomContext {
    override def values = Map("userId" -> "c6b272ae-0206-11e8-ba89-0ed5f89f718b")
  }

  logger.info(l"sends to userId=${"user2"}, dollars=${15}")
  logger.debug(l"should send to userId=${"user2"}, dollars=${4}")
  logger.warn(l"unused import")

  try {
    val a  = null
    a.getClass
  } catch {
    case f =>
      logger.error(l"dunno how to handle exception =${f.getMessage}")
  }



}
