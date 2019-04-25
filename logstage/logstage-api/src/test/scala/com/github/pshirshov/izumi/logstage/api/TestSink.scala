package com.github.pshirshov.izumi.logstage.api

import java.util.concurrent.ConcurrentLinkedQueue

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy

@ExposedTestScope
class TestSink(policy: Option[RenderingPolicy] = None) extends LogSink {
  private val messages = new ConcurrentLinkedQueue[Log.Entry]()
  private val renderedMessages = new ConcurrentLinkedQueue[String]()

  import scala.collection.JavaConverters._


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
