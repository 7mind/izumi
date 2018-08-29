package com.github.pshirshov.izumi.fundamentals.platform.strings

import scala.language.implicitConversions
import scala.util.Try

class IzString(s: String) {
  @inline def asBoolean(defValue: Boolean): Boolean = {
    asBoolean().getOrElse(defValue)
  }

  @inline def asBoolean(): Option[Boolean] = {
    Try(s.toBoolean).toOption
  }

  @inline def shift(delta: Int): String = {
    val shift = " " * delta
    s.split("\n").map(s => s"$shift$s").mkString("\n")
  }

  @inline def densify(): String = {
    s.replaceAll("\n\\s*\n", "\n\n").replaceAll("\\{\n\\s*\n", "{\n").replaceAll("\n\\s*\n\\}\n", "\n}").trim()
  }

  @inline def leftPad(len: Int): String = leftPad(len, ' ')

  @inline def leftPad(len: Int, elem: Char): String = {
    elem.toString * (len - s.length()) + s
  }

  @inline def minimize(leave: Int): String = {
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


  @inline def ellipsedLeftPad(limit: Int): String = {
    val limited = if (s.length > limit && s.length > 3) {
      s"...${s.takeRight(limit - 3)}"
    } else if (s.length > limit && s.length <= 3) {
      s"${s.takeRight(limit)}"
    } else {
      s
    }

    import IzString._
    limited.leftPad(limit, ' ')
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
