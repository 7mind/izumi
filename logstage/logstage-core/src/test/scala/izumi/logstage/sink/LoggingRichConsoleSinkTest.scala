package izumi.logstage.sink

import izumi.logstage.api.IzLogger
import izumi.logstage.api.rendering.logunits.StyleTag.{Bold, ColorTag}
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink.RichConsoleSink
import org.scalatest.wordspec.AnyWordSpec

class LoggingRichConsoleSinkTest extends AnyWordSpec {
  import LoggingRichConsoleSinkTest.*

  "log macro" should {
    "support rich console sink" in {
      val logger = setupConsoleLogger()
      logger.info("This is <b> bold </b> and <i> italic </i> and <u> underlined </u> and <r> reversed </r> text!")
      logger.info("This is <b> bold and also <i> italic and also <u> underlined text </u></i></b>")
      logger.info("This is <color:red> red </color:red> and <color:green> green </color:green> and <color:blue> blue </color:blue> text!")
      logger.info("""Drop tag if <unknown> ¯\\_(ツ)_/¯""")

      val testValue = "test"
      logger.info(s"<b>Stylized log with</b> $testValue <b>is working fine</b>")
      logger.info(s"<color:red>Even if value $testValue is inside style tag</color:red>")

      logger.info("Custom tags is <c:important> working </c:important> fine!")
      logger.info(s"<c:important>Even with $testValue !</c:important>")
    }
  }
}

object LoggingRichConsoleSinkTest {

  def setupConsoleLogger(): IzLogger = {
    val router = ConfigurableLogRouter(
      sink = new RichConsoleSink(Map("important" -> Seq(Bold, ColorTag("red"))))
    )

    IzLogger(router)
  }

}
