package com.github.pshirshov.izumi.idealingua.model.runtime.model

trait IDLEnum extends IDLGenerated {
  type Element <: IDLEnumElement

  def all: Seq[Element]

  def parse(value: String): Element
}
