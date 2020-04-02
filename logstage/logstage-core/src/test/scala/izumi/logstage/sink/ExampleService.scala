package izumi.logstage.sink

import com.github.ghik.silencer.silent
import izumi.functional.mono.SyncSafe
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.logstage.api.IzLogger
import izumi.logstage.api.rendering.LogstageCodec
import izumi.logstage.sink.ExampleService.ExampleDTO
import logstage.LogIO
import logstage.strict.LogIOStrict
import org.scalatest.Assertions
import org.scalatest.exceptions.TestFailedException

import scala.util.Random

@ExposedTestScope
@silent("[Ee]xpression.*logger")
@silent("missing interpolator")
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
    runStrict()
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
    implicit val logF: LogIO[Function0] = LogIO.fromLogger(logger)
    val suspended = LogIO[Function0].crit("Suspended message: clap your hands!")

    logger.crit("This should appear before the suspended message!")
    suspended()
  }

  private def runStrict(): Unit = {
    final case class NoInstance(x: Int)
    final case class YesInstance(x: Int)
    object YesInstance {
      implicit val codec: LogstageCodec[YesInstance] = _ write _.x
    }
    sealed trait Sealed
    object Sealed {
      implicit val codec: LogstageCodec[Sealed] = (writer, s) => s match {
        case Branch(x) => writer.write(s"""Branch("$x")""")
      }
      final case class Branch(x: String) extends Sealed
    }

    val logStrict: LogIOStrict[Function0] = LogIOStrict.fromLogger(logger)
    import Assertions._

    val exc = intercept[TestFailedException]{
      assertCompiles("""logStrict.crit(s"Suspended message: clap your hands! ${NoInstance(1)}")""")
    }
    assert(exc.getMessage() contains "Implicit search failed")
    val basic = {
      val instance = YesInstance(1)
      logStrict.crit(s"Suspended message: clap your hands! $instance")
    }
    val expressionsOk = logStrict.crit(s"Suspended message: clap your hands! ${YesInstance(2)}")
    val subtypesOk = {
      val branch = Sealed.Branch("subtypes are fine in strict")
      logStrict.crit(s"Suspended message: clap your hands! $branch")
    }
    val mapsOk = {
      val map = Map("Str" -> Sealed.Branch("subtypes are fine in strict"))
      logStrict.crit(s"Suspended message: clap your hands! $map")
    }
    basic()
    expressionsOk()
    subtypesOk()
    mapsOk()
  }

  private def makeException(message: String): RuntimeException = {
    val exception = new RuntimeException(message)
    exception.setStackTrace(exception.getStackTrace.slice(0, 3))
    exception
  }

  implicit val thunkSyncSafe: SyncSafe[Function0] = new SyncSafe[Function0] {
    override def syncSafe[A](unexceptionalEff: => A): () => A = () => unexceptionalEff
  }
}

object ExampleService {
  case class ExampleDTO(value: Int) {
    def method: Int = 1
  }
}
