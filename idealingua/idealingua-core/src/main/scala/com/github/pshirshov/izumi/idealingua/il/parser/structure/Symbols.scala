package com.github.pshirshov.izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Symbols {
  final def NLC[_:P]: P[Unit] = P("\r\n" | "\n" | "\r")
  final def String[_:P]: P[String] = P("\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
}


