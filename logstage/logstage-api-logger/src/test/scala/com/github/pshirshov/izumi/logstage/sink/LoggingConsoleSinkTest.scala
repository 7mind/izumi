package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.scalatest.WordSpec

import scala.util.{Failure, Success, Try}

class LoggingConsoleSinkTest extends WordSpec {
  import LoggingConsoleSinkTest._

  "Log macro" should {
    "support console backend" in {
      new ExampleService(setupConsoleLogger(None)).start()
    }


    "support console backend with custom rendering policy" in {
      val policy = s"$${level} $${ts}\t\t$${thread}\t$${location} $${custom-ctx} $${msg}"
      new ExampleService(setupConsoleLogger(Some(policy))).start()
    }


    "fail with duplicated entries in custom rendering policy" in {
      val duplicate = "level"
      val policy = s"""$${$duplicate} $${$duplicate} $${ts}\t\t$${thread}\t$${location} $${custom-ctx} $${msg}"""
      Try {
        new ExampleService(setupConsoleLogger(Some(policy))).start()
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
        new ExampleService(setupConsoleLogger(Some(policy))).start()
      } match {
        case Success(_) =>
          fail("Logging must fail on improperly formatted message template")
        case Failure(_) =>
      }
    }
  }
}

object LoggingConsoleSinkTest {

  def setupConsoleLogger(template: Option[String]): IzLogger = {
    IzLogger.basic(IzLogger.Level.Trace, new ConsoleSink(ConsoleSink.coloringPolicy(template)))
  }

}

