package izumi.fundamentals.platform.strings

class IzEscape(_toEscape: Set[Char], escapeChar: Char) {
  private val toEscape = _toEscape + escapeChar

  def escape(string: String): String = {
    val out = new StringBuilder()

    for (i <- string.indices) {
      val c = string.charAt(i)
      if (toEscape.contains(c)) {
        out.append(escapeChar)
        out.append(c)
      } else {
        out.append(c)
      }
    }

    out.toString()
  }

  def unescape(string: String): String = {
    val out = new StringBuilder()

    var inEscape = false

    for (i <- string.indices) {
      val c = string.charAt(i)
      if (inEscape) {
        out.append(c)
        inEscape = false
      } else if (c == escapeChar) {
        inEscape = true
      } else {
        out.append(c)
      }
    }

    out.toString()
  }

}
