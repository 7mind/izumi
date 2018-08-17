package com.github.pshirshov.izumi.idealingua.il.renderer

object RValue {
  def renderValue(value: Any): String = {
    value match {
      case l: Seq[_] =>
        l.map(renderValue).mkString("[", ",", "]")
      case l: Map[_, _] =>
        l.map {
          case (name, v) =>
            s"$name = ${renderValue(v)}"
        }.mkString("{", ",", "}")
      case s: String =>
        if (s.contains("\"")) {
          "\"\"\"" + s + "\"\"\""
        } else {
          "\"" + s + "\""
        }
      case o =>
        o.toString
    }
  }
}



