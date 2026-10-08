package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{ProtocolCodec, ProtocolMessage}

trait ProtocolFrameSink extends AutoCloseable {
  def writeFrame(frame: String): Unit
}

trait ProtocolFrameSource extends AutoCloseable {
  def readFrame(): Option[String]
}

final class FramedProtocolOutput(sink: ProtocolFrameSink) extends ProtocolOutput {
  override def accept(message: ProtocolMessage): Unit = synchronized {
    sink.writeFrame(ProtocolCodec.encode(message))
  }
}

private[runner] object ProtocolFrames {
  def validate(frame: String): Unit = {
    require(frame.nonEmpty, "Protocol channel frame must not be empty")
    require(frame.length <= ProtocolCodec.MaxFrameCharacters, "Protocol channel frame exceeds its character limit")
    require(frame.indexOf('\n') < 0 && frame.indexOf('\r') < 0, "Protocol channel frame must occupy one line")
    var index = 0
    while (index < frame.length) {
      val character = frame.charAt(index)
      if (Character.isHighSurrogate(character)) {
        require(index + 1 < frame.length && Character.isLowSurrogate(frame.charAt(index + 1)), "Protocol channel frame contains an unpaired surrogate")
        index += 2
      } else {
        require(!Character.isLowSurrogate(character), "Protocol channel frame contains an unpaired surrogate")
        index += 1
      }
    }
  }
}
