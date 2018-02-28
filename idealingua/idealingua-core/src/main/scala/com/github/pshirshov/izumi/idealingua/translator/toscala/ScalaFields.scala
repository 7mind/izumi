package com.github.pshirshov.izumi.idealingua.translator.toscala

case class ScalaFields(unique: List[ScalaField], nonUnique: List[ScalaField]) {
  def all: List[ScalaField] = unique ++ nonUnique
}
