package izumi.idealingua.translator.toscala.products

import scala.meta.{Defn, Template}


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

  protected def filterEmptyClasses(defns: List[Defn.Class]): List[Defn.Class] = {
    defns.filterNot(p => isEmpty(p.templ))
  }

  protected def filterEmptyObjects(defns: List[Defn.Object]): List[Defn.Object] = {
    defns.filterNot(p => isEmpty(p.templ))
  }

  private def isEmpty(t: Template): Boolean = t.stats.isEmpty && t.inits.isEmpty

  override def render: List[Defn] = {
    super.render ++ filterEmptyObjects(List(companion))
  }
}
