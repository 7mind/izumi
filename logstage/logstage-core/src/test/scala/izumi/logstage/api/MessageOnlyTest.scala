package izumi.logstage.api

import izumi.logstage.api.rendering.RenderingPolicy
import org.scalatest.wordspec.AnyWordSpec

class MessageOnlyTest extends AnyWordSpec {
  "IzLogger.messageOnly" should {
    "render only message text for plain strings" in {
      val sink = new TestSink(Some(RenderingPolicy.colorlessPolicy()))
      val logger = IzLogger(sink = sink)

      logger.messageOnly("Silent log")
      assert(sink.fetchRendered() == Seq("Silent log"))
    }

    "render only message text with args" in {
      val sink = new TestSink(Some(RenderingPolicy.colorlessPolicy()))
      val logger = IzLogger(sink = sink)

      val name = "Alice"
      logger.messageOnly(s"hello $name")
      assert(sink.fetchRendered() == Seq("hello name=Alice"))
    }

    "render only message text with args with silent names" in {
      val sink = new TestSink(Some(RenderingPolicy.colorlessPolicy()))
      val logger = IzLogger(sink = sink)

      val name = "Bob"
      logger.messageOnly(s"hello ${name -> null}")
      assert(sink.fetchRendered() == Seq("hello Bob"))
    }
  }
}
