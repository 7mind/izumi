package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import fastparse.NoWhitespace._
import fastparse._

class Positions(context: IDLParserContext) {
  def positioned[T](defparser: => P[T])(implicit v: P[_]): P[(InputPosition, T)] = {
    (Index ~ defparser ~ Index).map {
      case (start, value, stop) =>
        (InputPosition.Defined(start, stop, context.file), value)
    }
  }
}
