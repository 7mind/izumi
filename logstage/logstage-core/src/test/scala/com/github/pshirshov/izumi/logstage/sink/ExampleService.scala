package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.IzLogger
import logstage.LogIO

import scala.util.Random

@ExposedTestScope
class ExampleService(logger: IzLogger) {
  val field: String = "a value"
  def start(): Unit = {
    val loggerWithContext = logger("userId" -> "xxx")
    val loggerWithSubcontext = loggerWithContext("custom" -> "value")

    val justAnArg = "this is an argument"
    val justAList = List[Any](10, "green", "bottles")
    loggerWithContext.trace("This would be automatically extended")
    logger.debug(s"Service started. argument: $justAnArg, another arg: $justAList, Random value: ${Random.self.nextInt() -> "random value"}")
    loggerWithSubcontext.info("Just a string")
    logger.crit(s"This is an expression: ${Random.nextInt() -> "xxx"}")
    logger.crit(s"This is an expression with invisible argument name: ${Random.nextInt() -> "xxx" -> null}")
    val exception = new RuntimeException("Oy vey!")
    exception.setStackTrace(exception.getStackTrace.slice(0, 3))
    logger.crit(s"A failure happened: $exception")
    logger.crit(s"Failure cause: ${exception.getCause}")

    case class Example(v: Int, f: Throwable)
    val x = Example(1, exception)
    logger.crit(s"Argument name extracton: ${x.v}, ${x.f.getCause}, ${Example(2, exception).v}")

    logger.info(s"this.field value: $field")

    // cornercases
    val arg1 = 5
    val nullarg = null
    logger.warn("[Cornercase] non-interpolated expression: " + 1)
    logger.crit(s"[Cornercase] Anonymous expression: ${2 + 2 == 4}, another one: ${5 * arg1 == 25}")
    logger.crit(s"[Cornercase] null value: $nullarg")

    val badObj = new Object {
      override def toString: String = throw exception
    }
    logger.crit(s"[Cornercase] exception: ${badObj -> "bad"}")

    implicit val thunkSyncSafe: SyncSafe[Function0] = new SyncSafe[Function0] {
      override def syncSafe[A](unexceptionalEff: => A): () => A = () => unexceptionalEff
    }

    val logF = LogIO.fromLogger(logger)
    val suspended = logF.crit("Suspended message: clap your hands!")

    logger.crit("This should appear before the suspended message!")
    suspended()
  }

  def work(): Unit = {
    (1 to 100).foreach {
      i =>
        logger.debug(s"step $i")
    }
  }
}
