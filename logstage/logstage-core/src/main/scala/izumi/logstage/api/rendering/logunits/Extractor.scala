package izumi.logstage.api.rendering.logunits

import java.time.format.DateTimeFormatter

import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.Log
import izumi.logstage.api.rendering.RenderingOptions
import izumi.logstage.api.rendering.TimeOps

trait Extractor extends Renderer {
  def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode
}

object Extractor {

  class Constant(c: String) extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      LETree.TextNode(c)
    }
  }

  object Space extends Constant(" ")

  class ThreadName extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      LETree.TextNode(entry.context.dynamic.threadData.threadName)
    }
  }

  class ThreadId extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      LETree.TextNode(entry.context.dynamic.threadData.threadId.toString)
    }
  }

  class Timestamp(format: DateTimeFormatter) extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)

      val ts = TimeOps.convertToLocalTime(entry.context.dynamic.tsMillis)
      LETree.TextNode(format.format(ts))
    }
  }

  class Level(size: Int) extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      val lvl = entry.context.dynamic.level.toString
      LETree.TextNode(lvl.substring(0, math.min(size, lvl.length)))
    }
  }

  class SourcePosition() extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      LETree.TextNode(entry.context.static.position.toString)
    }
  }

  class LoggerName() extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      Quirks.discard(context)
      LETree.TextNode(entry.context.static.id.id)
    }
  }

  class Message extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {
      LETree.TextNode(LogFormat.Default.formatMessage(entry, context).message)
    }
  }

  class LoggerContext extends Extractor {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree.TextNode = {

      val values = entry.context.customContext.values

      val out = if (values.nonEmpty) {
        values
          .map {
            v => LogFormat.Default.formatKv(context.colored)(v.name, v.codec, v.value)
          }
          .mkString(", ")
      } else {
        ""
      }
      LETree.TextNode(out)
    }
  }

}
