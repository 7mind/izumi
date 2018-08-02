package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.StreamDirection

sealed trait RawStream

object RawStream {
  final case class Directed(name: String, direction: StreamDirection, signature: RawSimpleStructure, doc: Option[String]) extends RawStream
}
