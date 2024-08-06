package izumi.fundamentals.platform.strings.impl

final class String_IterableBytes_Syntax(private val s: Iterable[Byte]) extends AnyVal {
  def toHex: String = {
    s.foldLeft("") {
      case (str, b) => str ++ String.format("%02x", Byte.box(b))
    }.toUpperCase()
  }
}
