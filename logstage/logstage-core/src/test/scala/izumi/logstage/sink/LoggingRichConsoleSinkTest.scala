package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, Log, TestSink}
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.rendering.logunits.{BasicStyleTag, LogFormat, StyleTag}
import izumi.logstage.api.rendering.logunits.StyleTag.{Bold, ColorTag, Italic, Reversed, Underlined}
import izumi.logstage.sink.ConsoleSink.RichConsoleSink
import logstage.ConfigurableLogRouter
import izumi.distage.testkit.runner.spec.AnyWordSpec

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

    checkRendering(
      RenderingCase("render bold tag correctly", logger => logger.info("<b>bold</b>"), s"${Bold.render}bold${StyleTag.RESET}"),
      RenderingCase("render italic tag correctly", logger => logger.info("<i>italic</i>"), s"${Italic.render}italic${StyleTag.RESET}"),
      RenderingCase("render underlined tag correctly", logger => logger.info("<u>underlined</u>"), s"${Underlined.render}underlined${StyleTag.RESET}"),
      RenderingCase("render reversed tag correctly", logger => logger.info("<r>reversed</r>"), s"${Reversed.render}reversed${StyleTag.RESET}"),
      RenderingCase("render color tag correctly", logger => logger.info("<color:green>green</color:green>"), s"${ColorTag("green").render}green${StyleTag.RESET}"),
    )

    "render custom tag from stylesheet correctly" in {
      val stylesheet = Map("important" -> Seq(Bold, ColorTag("red")))
      assertRendered(s"${Bold.render}${ColorTag("red").render}important${StyleTag.RESET}", stylesheet)(_.info("<c:important>important</c:important>"))
      ()
    }

    checkRendering(
      RenderingCase(
        "render nested tags correctly",
        logger => logger.info("<b>bold <i>and italic</i> only bold</b>"),
        s"${Bold.render}bold ${Italic.render}and italic${StyleTag.RESET}${Bold.render} only bold${StyleTag.RESET}",
      ),
      RenderingCase("drop unknown tags gracefully", logger => logger.info("text <unknown> more text"), "text  more text"),
      RenderingCase("drop undefined custom tags gracefully", logger => logger.info("text <c:undefined> more text"), "text  more text"),
      RenderingCase("render text without tags unchanged", logger => logger.info("plain text without any tags"), "plain text without any tags"),
      RenderingCase(
        "render multiple tags in sequence correctly",
        logger => logger.info("<b>bold</b> and <i>italic</i> and <u>underlined</u>"),
        s"${Bold.render}bold${StyleTag.RESET} and ${Italic.render}italic${StyleTag.RESET} and ${Underlined.render}underlined${StyleTag.RESET}",
      ),
    )

    Seq(
      (Log.Level.Trace, RenderingCase("work with trace log level", logger => logger.trace("<b>trace message</b>"), s"${Bold.render}trace message${StyleTag.RESET}")),
      (Log.Level.Debug, RenderingCase("work with debug log level", logger => logger.debug("<i>debug message</i>"), s"${Italic.render}debug message${StyleTag.RESET}")),
      (Log.Level.Info, RenderingCase("work with info log level", logger => logger.info("<u>info message</u>"), s"${Underlined.render}info message${StyleTag.RESET}")),
      (
        Log.Level.Warn,
        RenderingCase(
          "work with warn log level",
          logger => logger.warn("<color:yellow>warn message</color:yellow>"),
          s"${ColorTag("yellow").render}warn message${StyleTag.RESET}",
        ),
      ),
      (
        Log.Level.Error,
        RenderingCase(
          "work with error log level",
          logger => logger.error("<color:red>error message</color:red>"),
          s"${ColorTag("red").render}error message${StyleTag.RESET}",
        ),
      ),
      (
        Log.Level.Crit,
        RenderingCase(
          "work with crit log level",
          logger => logger.crit("<b><color:red>critical message</color:red></b>"),
          s"${Bold.render}${ColorTag("red").render}critical message${StyleTag.RESET}${Bold.render}${StyleTag.RESET}",
        ),
      ),
    ).foreach {
      case (level, testcase) =>
        testcase.name in {
          val entry = assertRendered(testcase.expected, Map.empty)(testcase.write)
          assert(entry.context.dynamic.level == level)
        }
    }

  }
  private def checkRendering(cases: RenderingCase*): Unit = cases.foreach {
    testcase =>
      testcase.name in {
        assertRendered(testcase.expected, Map.empty)(testcase.write)
        ()
      }
  }

  private def assertRendered(expected: String, stylesheet: Map[String, Seq[BasicStyleTag]])(write: IzLogger => Unit): Log.Entry = {
    val (logger, testSink, richOptions) = setupRichTestLogger(stylesheet)
    write(logger)
    val Seq(entry) = testSink.fetch()
    assert(LogFormat.Default.formatMessage(entry, richOptions).message == expected)
    entry
  }

}

object LoggingRichConsoleSinkTest {
  private final case class RenderingCase(name: String, write: IzLogger => Unit, expected: String)

  def setupConsoleLogger(): IzLogger = {
    val router = ConfigurableLogRouter(
      sink = new RichConsoleSink(Map("important" -> Seq(Bold, ColorTag("red"))))
    )

    IzLogger(router)
  }

  def setupRichTestLogger(stylesheet: Map[String, Seq[BasicStyleTag]] = Map.empty): (IzLogger, TestSink, RenderingOptions) = {
    val richOptions = RenderingOptions.rich(stylesheet)
    val testSink = new TestSink(Some(new StringRenderingPolicy(richOptions, None)))
    val logger = IzLogger(threshold = Log.Level.Trace, sink = testSink)
    (logger, testSink, richOptions)
  }

}
