package izumi.fundamentals.platform.strings.impl

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

final class String_Syntax(private val s: String) extends AnyVal {
  @inline final def utf8: Array[Byte] = {
    s.getBytes(StandardCharsets.UTF_8)
  }

  @inline final def asBoolean(): Option[Boolean] = {
    try Some(s.toBoolean)
    catch {
      case e if NonFatal(e) => None
    }
  }

  @inline final def asBoolean(defValue: Boolean): Boolean = {
    try s.toBoolean
    catch {
      case e if NonFatal(e) => defValue
    }
  }

  @inline final def asInt(): Option[Int] = {
    try Some(s.toInt)
    catch {
      case e if NonFatal(e) => None
    }
  }

  @inline final def asInt(defValue: Int): Int = {
    try s.toInt
    catch {
      case e if NonFatal(e) => defValue
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
    val elen = ellipsis.length
    if (s.length > limit && s.length > elen) {
      s"$ellipsis${s.takeRight(limit - elen)}"
    } else if (s.length > limit && s.length <= elen) {
      s"${s.takeRight(limit)}"
    } else {
      s
    }
  }

  @inline final def rightEllipsed(limit: Int, ellipsis: String): String = {
    val elen = ellipsis.length
    if (s.length > limit && s.length > elen) {
      s"${s.take(limit - elen)}$ellipsis"
    } else if (s.length > limit && s.length <= elen) {
      s"${s.take(limit)}"
    } else {
      s
    }
  }

  @inline final def centerEllipsed(maxLength: Int, ellipsis: Option[String]): String = {
    if (s.length <= maxLength) {
      s
    } else {
      val half = maxLength / 2
      val (left, right) = ellipsis match {
        case Some(_) =>
          if (half * 2 < maxLength) {
            (half, half)
          } else {
            (half - 1, half)
          }
        case None =>
          if (half * 2 < maxLength) {
            (half + 1, half)
          } else {
            (half, half)
          }
      }

      s.take(left) + ellipsis.getOrElse("") + s.takeRight(right)
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

  def splitFirst(separator: Char): (String, String) = {
    s.indexOf(separator.toInt) match {
      case -1 => ("", s)
      case idx =>
        (s.substring(0, idx), s.substring(idx + 1, s.length))
    }
  }

  def splitLast(separator: Char): (String, String) = {
    s.lastIndexOf(separator.toInt) match {
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
