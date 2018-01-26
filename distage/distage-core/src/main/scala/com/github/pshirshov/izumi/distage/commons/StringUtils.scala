package com.github.pshirshov.izumi.distage.commons

object StringUtils {
  def shift(s: String, delta: Int): String = {
    val shift = " " * delta
    s.split("\n").map(s => s"$shift$s").mkString("\n")
  }

}
