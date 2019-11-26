package izumi.idealingua.il.parser.structure

import izumi.idealingua.model.il.ast.raw.typeid.ParsedId
import fastparse.NoWhitespace._
import fastparse._

trait Aggregates
  extends Separators
    with Identifiers {



  def enclosed[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P(("{" ~ any ~ defparser ~ any ~ "}") | "(" ~ any ~ defparser ~ any ~ ")")
  }

  def enclosedB[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P("[" ~ any ~ defparser ~ any ~ "]")
  }


  def starting[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(ParsedId, T)] = {
    kw(keyword, idShort ~ inline ~ defparser)
  }

  def block[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(ParsedId, T)] = {
    starting(keyword, enclosed(defparser))
  }


}


