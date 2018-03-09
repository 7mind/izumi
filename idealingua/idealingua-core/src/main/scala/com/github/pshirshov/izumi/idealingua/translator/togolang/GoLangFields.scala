package com.github.pshirshov.izumi.idealingua.translator.togolang

case class GoLangFields(unique: List[GoLangField], nonUnique: List[GoLangField]) {
  def all: List[GoLangField] = unique ++ nonUnique
}
