package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.fundamentals.platform.basics.IzBoolean._
import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable
import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.platform.time.IzTimeSafe
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.Styler.{PadType, TrimType}
import com.github.pshirshov.izumi.logstage.api.rendering.logunits.{Extractor, Renderer, Styler}
import com.github.pshirshov.izumi.logstage.api.{Log, rendering}

class StringRenderingPolicy(options: RenderingOptions, template: Option[Renderer.Aggregate] = None) extends RenderingPolicy {
  protected val context: RenderingOptions = {
    // it may be a good idea to remove these direct queries to the environment may

    val colorsEnabled = any(
      all(
        options.colored,
        System.getProperty("izumi.logstage.rendering.colored").asBoolean(true),
        IzJvm.terminalColorsEnabled,
      ),
      System.getProperty("izumi.logstage.rendering.colored.forced").asBoolean(false),
    )


    options.copy(colored = colorsEnabled)
  }

  private val renderer = template match {
    case Some(value) =>
      value
    case None =>
      StringRenderingPolicy.template
  }

  override def render(entry: Log.Entry): String = {
    val sb = new StringBuffer()

    sb.append(renderer.render(entry, context))

    if (options.withExceptions) {
      sb.append(traceThrowable(entry))
    }

    sb.toString
  }

  def traceThrowable(entry: Log.Entry): String = {
    entry.firstThrowable match {
      case Some(t) =>
        val builder = new StringBuilder
        builder.append('\n')
        if (context.colored) {
          builder.append(Console.YELLOW)
        }
        import IzThrowable._
        builder.append(t.stackTrace)
        if (context.colored) {
          builder.append(Console.RESET)
        }
        builder.toString()
      case None =>
        ""
    }
  }
}

object StringRenderingPolicy {
  val template = new Renderer.Aggregate(Seq(
    new Styler.LevelColor(Seq(
      new Extractor.Level(1),
      Extractor.Space,
      new Extractor.Timestamp(IzTimeSafe.ISO_LOCAL_DATE_TIME_3NANO)
    )),
    Extractor.Space,
    new Styler.Colored(
      Console.BLACK_B,
      Seq(
        new Styler.AdaptivePad(Seq(new Extractor.SourcePosition()), 8, PadType.Left, ' ')
      )
    ),
    Extractor.Space,
    Extractor.Space,
    new Styler.Trim(Seq(
      new Styler.Compact(
        Seq(new Extractor.LoggerName()),
        3
      )), 28, TrimType.Left, Some("…")
    ),
    Extractor.Space,
    new Extractor.Constant("["),

    new Styler.AdaptivePad(Seq(new Extractor.ThreadId()), 1, PadType.Left, ' '),
    new Extractor.Constant(":"),
    new Styler.AdaptivePad(Seq(new rendering.logunits.Styler.Trim(Seq(new rendering.logunits.Extractor.ThreadName()), 20, TrimType.Center, Some("…"))), 4, PadType.Right, ' '),
    new Extractor.Constant("]"),
    Extractor.Space,

    new Styler.TrailingSpace(Seq(new Extractor.LoggerContext())),
    new Extractor.Message(),
  ))

}
