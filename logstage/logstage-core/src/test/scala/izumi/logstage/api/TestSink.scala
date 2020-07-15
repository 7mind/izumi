package izumi.logstage.api

import java.util.concurrent.ConcurrentLinkedQueue

import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.language.Quirks._
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.RenderingPolicy

@ExposedTestScope
class TestSink(policy: Option[RenderingPolicy] = None) extends LogSink {
  private val messages = new ConcurrentLinkedQueue[Log.Entry]()
  private val renderedMessages = new ConcurrentLinkedQueue[String]()

  import scala.jdk.CollectionConverters._

  def fetch(): Seq[Log.Entry] = {
    messages.asScala.toSeq
  }

  def fetchRendered(): Seq[String] = {
    renderedMessages.asScala.toSeq
  }

  override def flush(e: Log.Entry): Unit = {
    messages.add(e).discard()
    policy.foreach {
      p =>
        renderedMessages.add(p.render(e)).discard()
    }

  }
}
