package com.github.pshirshov.izumi.fundamentals.platform.strings

import java.nio.charset.StandardCharsets

import scala.language.implicitConversions
import scala.util.Try





class IzString(s: String) {
  @inline final def utf8: Array[Byte] = {
    s.getBytes(StandardCharsets.UTF_8)
  }
  @inline final def asBoolean(defValue: Boolean): Boolean = {
    asBoolean().getOrElse(defValue)
  }

  @inline final def asBoolean(): Option[Boolean] = {
    Try(s.toBoolean).toOption
  }

  @inline final def shift(delta: Int): String = {
    val shift = " " * delta
    s.split('\n').map(s => s"$shift$s").mkString("\n")
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
  @inline def centerEllipsed(maxLength: Int, ellipsis: Option[String]): String = {
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



  def uncapitalize: String = {
    if (s == null) null
    else if (s.length == 0) ""
    else if (s.charAt(0).isLower) s
    else {
      val chars = s.toCharArray
      chars(0) = chars(0).toLower
      new String(chars)
    }
  }

  def camelToUnderscores: String = {
    "[A-Z\\d]".r.replaceAllIn(s, { m =>
      "_" + m.group(0).toLowerCase()
    })
  }

  def underscoreToCamel: String = {
    "_([a-z\\d])".r.replaceAllIn(s, { m =>
      m.group(1).toUpperCase()
    })
  }

  def splitFirst(separator: Char): (String, String) = {
    s.indexOf(separator.toInt) match {
      case -1 => ("", s)
      case idx =>
        (s.substring(0, idx), s.substring(idx+1, s.length))
    }
  }

  def splitLast(separator: Char): (String, String) = {
    s.lastIndexOf(separator.toInt) match {
      case -1 => ("", s)
      case idx =>
        (s.substring(0, idx), s.substring(idx+1, s.length))
    }
  }

}

class IzIterable[A](s: Iterable[A]) {
  def niceList(shift: String = " "): String = {
    if (s.nonEmpty) {
      val prefix = s"\n$shift- "
      s.mkString(prefix, prefix, "")
    } else {
      "ø"
    }
  }
}

object IzString {
  implicit def toRichString(s: String): IzString = new IzString(s)

  implicit def toRichIterable[A](s: Iterable[A]): IzIterable[A] = new IzIterable(s)
}
