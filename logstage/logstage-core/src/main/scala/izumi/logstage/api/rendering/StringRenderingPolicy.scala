package izumi.logstage.api.rendering

import izumi.fundamentals.platform.basics.IzBoolean._
import izumi.fundamentals.platform.jvm.IzJvm
import izumi.fundamentals.platform.time.IzTimeSafe
import izumi.logstage.DebugProperties
import izumi.logstage.api.rendering.logunits.Styler.{PadType, TrimType}
import izumi.logstage.api.rendering.logunits.{Extractor, Renderer, Styler}
import izumi.logstage.api.{Log, rendering}

class StringRenderingPolicy(
  options: RenderingOptions,
  template: Option[Renderer.Aggregate],
) extends RenderingPolicy {

  protected val context: RenderingOptions = {
    // it may be a good idea to remove these direct queries to the environment may

    val colorsEnabled = any(
      all(
        options.colored,
        DebugProperties.`izumi.logstage.rendering.colored`.boolValue(true),
        IzJvm.terminalColorsEnabled,
      ),
      DebugProperties.`izumi.logstage.rendering.colored.forced`.boolValue(false),
    )

    options.copy(colored = colorsEnabled)
  }

  private val renderer: Renderer.Aggregate = template match {
    case Some(value) =>
      value
    case None =>
      StringRenderingPolicy.template
  }

  override def render(entry: Log.Entry): String = {
    renderer.render(entry, context)
  }
}

object StringRenderingPolicy {
  val template: Renderer.Aggregate = new Renderer.Aggregate(
    Seq(
      new Styler.LevelColor(
        Seq(
          new Extractor.Level(1),
          Extractor.Space,
          new Extractor.Timestamp(IzTimeSafe.ISO_LOCAL_DATE_TIME_3NANO),
        )
      ),
      Extractor.Space,
      new Styler.Colored(
        Console.BLACK_B,
        Seq(
          new Styler.AdaptivePad(Seq(new Extractor.SourcePosition()), 8, PadType.Left, ' ')
        ),
      ),
      Extractor.Space,
      Extractor.Space,
      new Styler.Trim(
        Seq(
          new Styler.Compact(
            Seq(new Extractor.LoggerName()),
            3,
          )
        ),
        28,
        TrimType.Left,
        Some("…"),
      ),
      Extractor.Space,
      new Extractor.Constant("["),
      new Styler.AdaptivePad(Seq(new Extractor.ThreadId()), 1, PadType.Left, ' '),
      new Extractor.Constant(":"),
      new Styler.AdaptivePad(
        Seq(new rendering.logunits.Styler.Trim(Seq(new rendering.logunits.Extractor.ThreadName()), 20, TrimType.Center, Some("…"))),
        4,
        PadType.Right,
        ' ',
      ),
      new Extractor.Constant("]"),
      Extractor.Space,
      new Styler.TrailingSpace(Seq(new Extractor.LoggerContext())),
      new Extractor.Message(),
    )
  )

}
