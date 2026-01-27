package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, Log, TestSink}
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.rendering.logunits.{BasicStyleTag, LogFormat, StyleTag}
import izumi.logstage.api.rendering.logunits.StyleTag.{Bold, ColorTag, Italic, Reversed, Underlined}
import izumi.logstage.sink.ConsoleSink.RichConsoleSink
import logstage.ConfigurableLogRouter
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

    "render bold tag correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<b>bold</b>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}bold${StyleTag.RESET}")
    }

    "render italic tag correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<i>italic</i>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Italic.render}italic${StyleTag.RESET}")
    }

    "render underlined tag correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<u>underlined</u>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Underlined.render}underlined${StyleTag.RESET}")
    }

    "render reversed tag correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<r>reversed</r>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Reversed.render}reversed${StyleTag.RESET}")
    }

    "render color tag correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<color:green>green</color:green>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${ColorTag("green").render}green${StyleTag.RESET}")
    }

    "render custom tag from stylesheet correctly" in {
      val stylesheet = Map("important" -> Seq(Bold, ColorTag("red")))
      val (logger, testSink, richOptions) = setupRichTestLogger(stylesheet)
      logger.info("<c:important>important</c:important>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}${ColorTag("red").render}important${StyleTag.RESET}")
    }

    "render nested tags correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<b>bold <i>and italic</i> only bold</b>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}bold ${Italic.render}and italic${StyleTag.RESET}${Bold.render} only bold${StyleTag.RESET}")
    }

    "drop unknown tags gracefully" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("text <unknown> more text")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == "text  more text")
    }

    "drop undefined custom tags gracefully" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("text <c:undefined> more text")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == "text  more text")
    }

    "render text without tags unchanged" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("plain text without any tags")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == "plain text without any tags")
    }

    "render multiple tags in sequence correctly" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<b>bold</b> and <i>italic</i> and <u>underlined</u>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}bold${StyleTag.RESET} and ${Italic.render}italic${StyleTag.RESET} and ${Underlined.render}underlined${StyleTag.RESET}")
    }

    "work with trace log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.trace("<b>trace message</b>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}trace message${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Trace)
    }

    "work with debug log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.debug("<i>debug message</i>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Italic.render}debug message${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Debug)
    }

    "work with info log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.info("<u>info message</u>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Underlined.render}info message${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Info)
    }

    "work with warn log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.warn("<color:yellow>warn message</color:yellow>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${ColorTag("yellow").render}warn message${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Warn)
    }

    "work with error log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.error("<color:red>error message</color:red>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${ColorTag("red").render}error message${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Error)
    }

    "work with crit log level" in {
      val (logger, testSink, richOptions) = setupRichTestLogger()
      logger.crit("<b><color:red>critical message</color:red></b>")
      val Seq(entry) = testSink.fetch()
      val rendered = LogFormat.Default.formatMessage(entry, richOptions)
      assert(rendered.message == s"${Bold.render}${ColorTag("red").render}critical message${StyleTag.RESET}${Bold.render}${StyleTag.RESET}")
      assert(entry.context.dynamic.level == Log.Level.Crit)
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

  def setupRichTestLogger(stylesheet: Map[String, Seq[BasicStyleTag]] = Map.empty): (IzLogger, TestSink, RenderingOptions) = {
    val richOptions = RenderingOptions.rich(stylesheet)
    val testSink = new TestSink(Some(new StringRenderingPolicy(richOptions, None)))
    val logger = IzLogger(threshold = Log.Level.Trace, sink = testSink)
    (logger, testSink, richOptions)
  }

}
