package com.github.pshirshov.izumi.idealingua.runtime.model

trait IDLEnum extends IDLGenerated {
  type Element <: IDLEnumElement

  def all: Seq[Element]

  def parse(value: String): Element
}
