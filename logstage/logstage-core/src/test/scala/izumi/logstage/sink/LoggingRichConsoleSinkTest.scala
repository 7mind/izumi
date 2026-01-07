package izumi.logstage.sink

import izumi.logstage.api.IzLogger
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink.RichConsoleSink
import logstage.{Log, LogQueue}
import org.scalatest.wordspec.AnyWordSpec

class LoggingRichConsoleSinkTest extends AnyWordSpec {
  import LoggingRichConsoleSinkTest.*

  "log macro" should {
    "support rich console sink" in {
      val logger = setupConsoleLogger()
      logger.info("This is <b> bold </b> and <i> italic </i> and <u> underlined </u> and <r> reversed </r> text!")
      logger.info("This is <b> bold and also <i> italic and also <u> underlined text </u></i></b>")
      logger.info("This is <c:red> red </c:red> and <c:green> green </c:green> and <c:blue> blue </c:blue> text!")
      logger.info("""Drop tag if <unknown> ¯\\_(ツ)_/¯""")
    }
  }
}

object LoggingRichConsoleSinkTest {

  def setupConsoleLogger(): IzLogger = {
    val router = ConfigurableLogRouter(
      Log.Level.Trace,
      Seq(RichConsoleSink),
      Map.empty,
      LogQueue.Immediate,
    )

    IzLogger(router)
  }

}
