package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawPositioned
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath
import fastparse.NoWhitespace._
import fastparse._

class Positions(context: IDLParserContext) {

  case class Indexed[T](value: T, start: Int, stop: Int, location: FSPath)

  def indexed[T](defparser: => P[T])(implicit v: P[_]): P[Indexed[T]] = {
    (Index ~ defparser ~ Index).map {
      case (start, value, stop) =>
        Indexed(value, start, stop, context.file)
    }
  }

  def IP[T <: RawPositioned](defparser: => P[T])(implicit v: P[_]): P[T] = {
    indexed(defparser).map {
      i =>
        i.value.updatePosition(i.start, i.stop, i.location).asInstanceOf[T]
    }
  }
}
