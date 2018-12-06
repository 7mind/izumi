package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParserPosition, RawPositioned}
import fastparse.NoWhitespace._
import fastparse._

class Positions(context: IDLParserContext) {
  def positioned[T](defparser: => P[T])(implicit v: P[_]): P[(InputPosition, T)] = {
    (Index ~ defparser ~ Index).map {
      case (start, value, stop) =>
        (InputPosition.Defined(start, stop, context.file), value)
    }
  }

  def IP[T <: RawPositioned](defparser: => P[T])(implicit v: P[_]): P[T] = {
    xpositioned(defparser).map {
      i =>
        i.value.updatePosition(i).asInstanceOf[T]
    }
  }

  private def xpositioned[T](defparser: => P[T])(implicit v: P[_]): P[ParserPosition[T]] = {
    (Index ~ defparser ~ Index).map {
      case (start, value, stop) =>
        ParserPosition(value, start, stop, context.file)
    }
  }
}
