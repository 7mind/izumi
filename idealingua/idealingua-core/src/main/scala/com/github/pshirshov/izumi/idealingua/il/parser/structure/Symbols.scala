package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Symbols {
  final def NLC[_:P] = P("\r\n" | "\n" | "\r")
  final def String[_:P] = P("\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
}


