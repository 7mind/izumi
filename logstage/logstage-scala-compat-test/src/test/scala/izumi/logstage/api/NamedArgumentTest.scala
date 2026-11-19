package izumi.logstage.api

import izumi.logstage.api.Log.{LogArg, Message}
import izumi.logstage.api.rendering.LogstageCodec
import org.scalatest.wordspec.AnyWordSpec

class NamedArgumentTest extends AnyWordSpec {
  "Argument extraction macro" should {
    "rename an interpolated argument with ->" in {
      val arg = "test"
      val message = Message(s"$arg ${arg -> "renamed"}")

      assert(
        message.args == List(
          LogArg(Seq("arg"), arg, hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          LogArg(Seq("renamed"), arg, hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
        )
      )
    }
  }
}
