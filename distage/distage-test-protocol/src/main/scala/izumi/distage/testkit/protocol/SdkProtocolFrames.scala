package izumi.distage.testkit.protocol

object SdkProtocolFrames {
  final case class Frame(selectorId: String, payload: String)

  final val SelectorId = "$distage-protocol-v4"
  final val PartSelectorId = "$distage-protocol-v4-part"
  // Modified UTF-8 uses at most three bytes per UTF-16 character; leave room for the part header.
  final val MaxPartCharacters = 16000
  final val MaxModifiedUtfBytes = 65535

  def encode(frame: String): Vector[Frame] = {
    validate(frame)
    if (frame.length <= MaxPartCharacters) Vector(Frame(SelectorId, frame))
    else frame.grouped(MaxPartCharacters).zipWithIndex.map { case (part, index) =>
      Frame(PartSelectorId, s"${frame.length}:${index * MaxPartCharacters}:$part")
    }.toVector
  }

  final class Decoder {
    private final class Pending(val owner: String, val length: Int, val content: StringBuilder)
    private var pending = Option.empty[Pending]

    def accept(owner: String, frame: Frame): Option[String] = {
      frame.selectorId match {
        case SelectorId =>
          requireComplete()
          validate(frame.payload)
          Some(frame.payload)
        case PartSelectorId =>
          val first = frame.payload.indexOf(':')
          val second = frame.payload.indexOf(':', first + 1)
          require(first > 0 && second > first + 1, "Malformed SDK frame part header")
          val length = frame.payload.substring(0, first).toInt
          val offset = frame.payload.substring(first + 1, second).toInt
          val part = frame.payload.substring(second + 1)
          require(length > MaxPartCharacters && length <= ProtocolCodec.MaxFrameCharacters, "SDK frame length is outside its character limit")
          require(offset >= 0 && offset < length, "SDK frame part offset is outside its frame")
          require(part.length == math.min(MaxPartCharacters, length - offset), "SDK frame part has an unexpected length")
          require(!part.contains('\n') && !part.contains('\r'), "SDK frame part must occupy one channel line")
          val current = pending match {
            case Some(value) => value
            case None =>
              require(offset == 0, "SDK frame does not start with its first part")
              val value = new Pending(owner, length, new StringBuilder)
              pending = Some(value)
              value
          }
          require(current.owner == owner && current.length == length, "SDK frame parts changed their owner or length")
          require(current.content.length == offset, "SDK frame parts are not contiguous")
          val _ = current.content.append(part)
          if (current.content.length == length) {
            pending = None
            Some(current.content.result())
          } else None
        case _ => throw new IllegalArgumentException("Unknown SDK protocol selector")
      }
    }

    def requireComplete(): Unit = require(pending.isEmpty, "SDK channel ended with an incomplete frame")
  }

  private def validate(frame: String): Unit = {
    require(frame.nonEmpty && frame.length <= ProtocolCodec.MaxFrameCharacters, "SDK frame is outside its character limit")
    require(!frame.contains('\n') && !frame.contains('\r'), "SDK frame must occupy one channel line")
  }
}
