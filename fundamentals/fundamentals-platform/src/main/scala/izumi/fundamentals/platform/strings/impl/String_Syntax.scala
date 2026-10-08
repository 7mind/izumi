package izumi.fundamentals.platform.strings.impl

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

final class String_Syntax(private val s: String) extends AnyVal {
  @inline final def utf8: Array[Byte] = {
    s.getBytes(StandardCharsets.UTF_8)
  }

  @inline final def asBoolean(): Option[Boolean] = parse(Some(s.toBoolean), None)

  @inline final def asBoolean(defValue: Boolean): Boolean = parse(s.toBoolean, defValue)

  @inline final def asInt(): Option[Int] = parse(Some(s.toInt), None)

  @inline final def asInt(defValue: Int): Int = parse(s.toInt, defValue)

  private def parse[A](value: => A, fallback: => A): A = {
    try value
    catch {
      case e if NonFatal(e) => fallback
    }
  }

  @inline final def shift(delta: Int, fill: String = " "): String = {
    val shift = fill * delta
    s.split("\\\n", -1).map(s => s"$shift$s").mkString("\n")
  }

  @inline final def densify(): String = {
    s.replaceAll("\n\\s*\n", "\n\n").replaceAll("\\{\n\\s*\n", "{\n").replaceAll("\n\\s*\n\\}\n", "\n}").trim()
  }

  @inline final def leftPad(len: Int): String = leftPad(len, ' ')

  @inline final def leftPad(len: Int, elem: Char): String = {
    elem.toString * (len - s.length()) + s
  }

  @inline final def minimize(leave: Int): String = {
    val parts = s.split('.').toVector
    if (parts.size < leave) {
      s
    } else {
      val toLeave = parts.takeRight(leave)
      val theRest = parts.take(parts.size - leave)
      val minimized = theRest
        .filterNot(_.isEmpty).map(_.substring(0, 1))
      (minimized ++ toLeave).mkString(".")
    }
  }

  @inline final def leftEllipsed(limit: Int, ellipsis: String): String = {
    ellipsed(limit, ellipsis)(s.takeRight, ellipsis + _)
  }

  @inline final def rightEllipsed(limit: Int, ellipsis: String): String = {
    ellipsed(limit, ellipsis)(s.take, _ + ellipsis)
  }

  private def ellipsed(limit: Int, ellipsis: String)(take: Int => String, append: String => String): String = {
    val elen = ellipsis.length
    if (s.length <= limit) {
      s
    } else if (s.length > elen) {
      append(take(limit - elen))
    } else {
      take(limit)
    }
  }

  @inline final def centerEllipsed(maxLength: Int, ellipsis: Option[String]): String = {
    if (s.length <= maxLength) {
      s
    } else {
      val half = maxLength / 2
      val left = half + (if (half * 2 < maxLength) 1 else 0)
      val prefix = ellipsis match {
        case Some(_) => left - 1
        case None => left
      }

      s.take(prefix) + ellipsis.getOrElse("") + s.takeRight(half)
    }
  }

  @inline def split2(splitter: Char): (String, String) = {
    val parts = s.split(splitter)
    (parts.head, parts.tail.mkString(splitter.toString))
  }

  def uncapitalize: String = {
    if (s == null) null
    else if (s.isEmpty) ""
    else if (s.charAt(0).isLower) s
    else {
      val chars = s.toCharArray
      chars(0) = chars(0).toLower
      new String(chars)
    }
  }

  def camelToUnderscores: String = {
    if (s.isEmpty) {
      s
    } else {
      s"${s.head.toLower}${"[A-Z\\d]".r.replaceAllIn(s.tail, m => "_" + m.group(0).toLowerCase())}"
    }
  }

  def underscoreToCamel: String = {
    if (s.isEmpty) {
      s
    } else {
      s"${s.head.toUpper}${"_([a-z\\d])".r.replaceAllIn(s.tail, m => m.group(1).toUpperCase())}"
    }
  }

  def splitFirst(separator: Char): (String, String) = splitAtIndex(s.indexOf(separator.toInt))

  def splitLast(separator: Char): (String, String) = splitAtIndex(s.lastIndexOf(separator.toInt))

  private def splitAtIndex(index: Int): (String, String) = {
    index match {
      case -1 => ("", s)
      case idx =>
        (s.substring(0, idx), s.substring(idx + 1, s.length))
    }
  }

  def block(delta: Int, open: String, close: String): String = {
    s"$open${shift(delta)}$close"
  }

  def listing(header: String): String = {
    import izumi.fundamentals.platform.strings.IzString.*
    header + "\n" + listing().shift(1, "| ")
  }

  def listing(): String = {
    val lines = s.split('\n')
    import scala.math.*
    val magnitude = log10(lines.length.toDouble)
    val min = floor(magnitude).toInt
    val max = ceil(magnitude).toInt
    val pad = if (min == max) {
      min + 1
    } else {
      max
    }

    import izumi.fundamentals.platform.strings.IzString.*
    lines.zipWithIndex
      .map {
        case (l, i) =>
          s"${(i + 1).toString.leftPad(pad)}: $l"
      }
      .mkString("\n")
  }
}
