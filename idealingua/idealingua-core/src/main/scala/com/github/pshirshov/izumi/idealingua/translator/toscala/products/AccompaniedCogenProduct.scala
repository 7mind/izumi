package com.github.pshirshov.izumi.idealingua.translator.toscala.products

import scala.meta.Defn

trait UnaryCogenProduct[T <: Defn] extends RenderableCogenProduct {
  def more: List[Defn]

  def defn: T

  def render: List[Defn] = {
    List(defn) ++ more
  }
}


trait AccompaniedCogenProduct[T <: Defn] extends UnaryCogenProduct[T] {
  def companion: Defn.Object

  override def render: List[Defn] = {
    super.render ++ List(companion).filter(_.templ.stats.nonEmpty)
  }
}
