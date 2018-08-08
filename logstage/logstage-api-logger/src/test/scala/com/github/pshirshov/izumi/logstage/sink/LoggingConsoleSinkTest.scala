package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import org.scalatest.WordSpec
import LoggingAsyncSinkTest._

import scala.util.{Failure, Success, Try}

class LoggingConsoleSinkTest extends WordSpec {
  import LoggingConsoleSinkTest._

  "Log macro" should {
    "support console backend" in {
      new ExampleService(setupConsoleLogger()).start()
    }


    "support console backend with custom rendering policy" in {
      val policy = s"$${level} $${ts}\t\t$${thread}\t$${location} $${custom-ctx} $${msg}"
      new ExampleService(setupConsoleLogger(ConsoleSink.coloringPolicy(Some(policy)))).start()
    }


    "fail with duplicated entries in custom rendering policy" in {
      val duplicate = "level"
      val policy = s"""$${$duplicate} $${$duplicate} $${ts}\t\t$${thread}\t$${location} $${custom-ctx} $${msg}"""
      Try {
        new ExampleService(setupConsoleLogger(ConsoleSink.coloringPolicy(Some(policy)))).start()
      } match {
        case Success(_) =>
          fail("Logger must be unavailable when duplicates are")
        case Failure(f) =>
          assert(f.getMessage.contains(duplicate))
      }
    }

    "fail with enclosed braces in custom rendering policy" in {
      val policy = s"""$${ts{\t\t$${thread}\t$${location} $${custom-ctx} $${msg}"""
      Try {
        new ExampleService(setupConsoleLogger(ConsoleSink.coloringPolicy(Some(policy)))).start()
      } match {
        case Success(_) =>
          fail("Logging must fail on improperly formatted message template")
        case Failure(_) =>
      }
    }
  }
}

object LoggingConsoleSinkTest {

  def setupConsoleLogger(policy: StringRenderingPolicy = ConsoleSink.coloringPolicy()): IzLogger = {
    IzLogger.configureLogger(new ConsoleSink(policy))
  }

}

