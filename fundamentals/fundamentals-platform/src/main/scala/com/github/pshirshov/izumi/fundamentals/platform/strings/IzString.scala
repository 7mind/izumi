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

  @inline def ellipsedLeftPad(limit: Int): String = {
    val limited = if (s.length > limit && s.length >3) {
      s"...${s.takeRight(limit - 3)}"
    } else if (s.length > limit && s.length <= 3) {
      s"${s.takeRight(limit)}"
    } else {
      s
    }

    import IzString._
    limited.leftPad(limit, ' ')
  }

  @inline def upperFirst: String = s(0).toUpper + s.substring(1)
  @inline def lowerFirst: String = s(0).toLower + s.substring(1)

}

object IzString {
  implicit def toRich(s: String): IzString = new IzString(s)
}
