package izumi.idealingua.il.parser.structure

import fastparse._
import fastparse.NoWhitespace._

trait Symbols {
  def NLC[_:P]: P[Unit] = P("\r\n" | "\n" | "\r")
  def String[_:P]: P[String] = P("\"" ~ CharsWhile(c => c != '"').rep().! ~ "\"")
}


