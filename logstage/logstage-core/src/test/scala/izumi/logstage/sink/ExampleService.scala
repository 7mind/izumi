package izumi.logstage.sink

import com.github.ghik.silencer.silent
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.logstage.api.IzLogger
import izumi.logstage.sink.ExampleService.ExampleDTO
import logstage.LogIO

import scala.util.Random

@ExposedTestScope
@silent("[Ee]xpression.*logger")
class ExampleService(logger: IzLogger) {
  val field: String = "a value"

  def start(): Unit = {
    val justAnArg = "this is an argument"
    val justAList = List[Any](10, "green", "bottles")

    logger.info(s"A simple message: $justAnArg")
    logger.info(s"A simple message with iterable argument: $justAList")

    val loggerWithContext = logger("userId" -> "xxx")
    loggerWithContext.trace(s"Custom context will be added into this message. Dummy: $justAnArg")
    val loggerWithSubcontext = loggerWithContext("custom" -> "value")
    loggerWithSubcontext.info(s"Both custom contexts will be added into this message. Dummy: $justAnArg")


    logger.crit(s"This is an expression with user-assigned name: ${Random.nextInt() -> "random value"}")
    logger.crit(s"This is an expression with user-assigned name which will be hidden from text representations: ${Random.nextInt() -> "random value" -> null}")

    logger.info(s"This name will be converted from camel case to space-separated ('just and arg'). Note: spaces are replaced with underscores in non-colored sinks. ${justAnArg -> ' '}")
    logger.info(s"..Same with invisible name: ${justAnArg -> ' ' -> null}")

    val duplicatedParam = "DuplicatedParamVal"
    logger.crit(s"Duplicated parameters will be displayed as lists in json form and with indexes in text form: $duplicatedParam, $duplicatedParam")

    val x = ExampleDTO(1)
    logger.crit(s"Argument name extracton: ${x.value}, ${x.method}")
    logger.info(s"this.field value will have name 'field': $field")

    testExceptions()
    testCornercases()
    runMonadic()
  }

  def triggerManyMessages(): Unit = {
    (1 to 100).foreach {
      i =>
        logger.debug(s"step $i")
    }
  }

  private def testExceptions(): Unit = {
    val exception = makeException("Oy vey!")
    logger.crit(s"An exception will be displayed with stack: $exception")
    logger.crit(s"Getter method names are supported: ${exception.getCause}")
  }

  private def testCornercases(): Unit = {
    val arg1 = 5
    val nullarg = null
    val exception = makeException("Boom!")
    logger.warn("[Cornercase] non-interpolated expression: " + 1)
    logger.crit(s"[Cornercase] Anonymous expression: ${2 + 2 == 4}, another one: ${5 * arg1 == 25}")
    logger.crit(s"[Cornercase] null value: $nullarg")

    val badObj = new Object {
      override def toString: String = throw exception
    }
    logger.crit(s"[Cornercase] exception: ${badObj -> "bad"}")
  }

  private def runMonadic(): Unit = {
    implicit val thunkSyncSafe: SyncSafe[Function0] = new SyncSafe[Function0] {
      override def syncSafe[A](unexceptionalEff: => A): () => A = () => unexceptionalEff
    }

    implicit val logF: LogIO[Function0] = LogIO.fromLogger(logger)
    val suspended = LogIO[Function0].crit("Suspended message: clap your hands!")

    logger.crit("This should appear before the suspended message!")
    suspended()
  }

  private def makeException(message: String): RuntimeException = {
    val exception = new RuntimeException(message)
    exception.setStackTrace(exception.getStackTrace.slice(0, 3))
    exception
  }
}

object ExampleService {
  case class ExampleDTO(value: Int) {
    def method: Int = 1
  }
}
