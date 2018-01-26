package com.github.pshirshov.izumi.logstage.example

import com.github.pshirshov.izumi.logstage.api.logger._
import com.github.pshirshov.izumi.logstage.model.Log


// Usage

trait Logging extends WithLogContext {
  val logger = new BoundLogger(Logging.logging)(this)
}

object Logging {
  val logging = new RoutingLogReceiver {


    override def level: Log.Level = Log.Level.Debug

    override protected def logConfigService: LogConfigService = new LogConfigService {
      val logFilter = new LogFilter {}
      val sink = new LogSink {}
      val mapping = new LogMapping(filter = logFilter, sink)

      override def config(e: Log.Entry): Seq[LogMapping] = Seq(mapping)
    }
  }
}


object LoggedApp extends App with Logging  {

  implicit def customLoggingContext: Log.CustomContext = new Log.CustomContext {
    override def values = Map("userId" -> "c6b272ae-0206-11e8-ba89-0ed5f89f718b")
  }

  val userId = "c6b272ae-0206-11e8-ba89-0ed5f89f718b"

  val amount = 4

  import logger._
  debug(m"should send to ${userId} ${amount} within ${5} minutes")
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
