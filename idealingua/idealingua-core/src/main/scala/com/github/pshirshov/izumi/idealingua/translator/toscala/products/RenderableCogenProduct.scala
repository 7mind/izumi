package com.github.pshirshov.izumi.idealingua.translator.toscala.products

import scala.meta.{Defn, Term}


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
