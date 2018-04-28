package com.github.pshirshov.izumi.logstage

import java.util.concurrent.ConcurrentLinkedQueue

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink

@ExposedTestScope
class TestSink extends LogSink {
  private val messages = new ConcurrentLinkedQueue[Log.Entry]()

  import scala.collection.JavaConverters._


  def fetch: Seq[Log.Entry] = messages.asScala.toSeq

  override def flush(e: Log.Entry): Unit = {
    Quirks.discard(messages.add(e))
  }
}
