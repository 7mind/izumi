package org.bitbucket.pshirshov.izumi.example

import org.bitbucket.pshirshov.izumi.logger.Log.LogMapping
import org.bitbucket.pshirshov.izumi.logger._
import org.bitbucket.pshirshov.izumi.logger.api.Logger


// Usage
import Logger._

class Sample(logger: Logger) extends WithLogContext { // this has to be resolved on DI level
  logger.debug(l"message x=${1} and y=${2}")
  logger.debug(l"message ${"asdad"} x=${1} and y=${2}, ${4}")
}

// or (better, less invasive)

class Sample1(logger: BoundLogger) { // this has to be resolved on DI level
  implicit def myVeryCustomContext: Log.CustomContext = new Log.CustomContext {
    override def values = Map("x" -> "y")
  }

  logger.debug(l"message x=${1} and y=${2}")

}



object LoggedApp extends App {
  val logging = new Logger {
    override protected def logConfigService: LogConfigService = new LogConfigService {


      val logFilter = new LogFilter {
      }

      val sink = new LogSink {
      }

      val mapping = new LogMapping(filter = logFilter, sink)


      override def config(e: Log.Entry): Seq[LogMapping] = Seq(mapping)
    }
  }

  val sample = new Sample(logging)
}

