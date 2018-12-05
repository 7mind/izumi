package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

case class ParserPosition[T](value: T, start: Int, stop: Int, location: FSPath) {
  def toInputPos: InputPosition.Defined = InputPosition.Defined(start, stop, location)
}

trait RawPositioned {
  def updatePosition(position: ParserPosition[_]): RawPositioned

}

trait RawWithMeta extends RawPositioned {
  def meta: RawNodeMeta

  def updateMeta(f: RawNodeMeta => RawNodeMeta): RawTypeDef

  override def updatePosition(position: ParserPosition[_]): RawTypeDef = {
    updateMeta {
      meta =>
        meta.copy(position = position.toInputPos)
    }
  }

}
