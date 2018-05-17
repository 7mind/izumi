package com.github.pshirshov.izumi.idealingua.translator.toscala.products

import scala.meta.Defn


trait RenderableCogenProduct {
  def preamble: String
  def render: List[Defn]
  def isEmpty: Boolean = render.isEmpty
}

object RenderableCogenProduct {
  def empty: RenderableCogenProduct = new RenderableCogenProduct {
    override def render: List[Defn] = List.empty
    override def preamble: String = ""
  }
}


trait UnaryCogenProduct[T <: Defn] extends RenderableCogenProduct {
  def defn: T

  override def render: List[Defn] = List(defn)
}


trait MultipleCogenProduct[T <: Defn] extends UnaryCogenProduct[T] {
  def more: List[Defn]

  def defn: T

  override def render: List[Defn] = {
    super.render ++ more
  }
}


trait AccompaniedCogenProduct[T <: Defn] extends MultipleCogenProduct[T] {
  def companion: Defn.Object

  override def render: List[Defn] = {
    super.render ++ List(companion).filter(_.templ.stats.nonEmpty)
  }
}
