package izumi.distage.testkit.protocol

import java.nio.charset.StandardCharsets

private[protocol] object UnicodeFrameFixtures {
  private final val MaxAsciiCharacter = 0x7f
  private final val UnicodeEscapeCharacters = 6

  def run(verify: (Boolean, String) => Unit, reject: (ProtocolMessage, String) => Unit): Unit = {
    val values = Vector("\uD800", "\uDC00", "A\uD800B", "\uD800\uD800", "é", "€", "\uD83D\uDE00", "雪\n\\\"")
    values.foreach { value =>
      val message = ProtocolMessage.Cancel(RunId(value))
      val frame = ProtocolCodec.encode(message)
      val transported = new String(frame.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
      verify(transported == frame, "Encoded protocol frames must retain every UTF-16 code unit during UTF-8 transport")
      verify(ProtocolCodec.decode(transported) == Right(message), "Transported Unicode, surrogate and escaped strings must round-trip")
      verify(frame.forall(_ <= MaxAsciiCharacter), "Protocol output must escape non-ASCII characters")
    }
    val overhead = ProtocolCodec.encode(ProtocolMessage.Cancel(RunId("x"))).length - 1
    val payloadCharacters = ProtocolCodec.MaxFrameCharacters - overhead
    val escapes = payloadCharacters / UnicodeEscapeCharacters
    val padding = payloadCharacters % UnicodeEscapeCharacters
    val value = "€" * escapes + "x" * padding
    val boundary = ProtocolMessage.Cancel(RunId(value))
    val frame = ProtocolCodec.encode(boundary)
    verify(frame.length == ProtocolCodec.MaxFrameCharacters, "Frame limit must include Unicode escape expansion")
    verify(ProtocolCodec.decode(frame) == Right(boundary), "Unicode-expanded frame boundary must round-trip")
    reject(ProtocolMessage.Cancel(RunId(value + "x")), "character limit")
  }
}
