package com.github.pshirshov.izumi.idealingua.translator.toscala.products

import scala.meta.Defn


trait AbstractCogenProduct[T <: Defn] extends RenderableCogenProduct {
  def more: List[Defn]

  def defn: T

  def companion: Defn.Object
}
