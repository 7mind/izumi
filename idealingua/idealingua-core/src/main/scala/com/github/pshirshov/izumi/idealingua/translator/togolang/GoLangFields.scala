package com.github.pshirshov.izumi.idealingua.translator.togolang

case class GoLangFields(unique: List[GoLangField], nonUnique: List[GoLangField] = List.empty) {
  def all: List[GoLangField] = unique ++ nonUnique

  def packageDependencies: Set[String] = if (hasTimeField) Set("time") else Set.empty

  def hasTimeField: Boolean = all.exists(_.fieldType.isInstanceOf[Time])
}
