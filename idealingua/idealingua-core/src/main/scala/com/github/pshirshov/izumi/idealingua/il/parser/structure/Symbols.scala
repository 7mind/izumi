package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse.all._

trait Symbols {
  final val NLC = P("\r\n" | "\n" | "\r")
  final val String = P("\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
}


