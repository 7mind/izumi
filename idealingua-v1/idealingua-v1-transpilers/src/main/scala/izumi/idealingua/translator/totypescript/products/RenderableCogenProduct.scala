package izumi.idealingua.translator.totypescript.products

trait RenderableCogenProduct {
  def preamble: String
  def render: List[String]
  def renderHeader: List[String]
  def isEmpty: Boolean = render.isEmpty
}

object RenderableCogenProduct {
  def empty: RenderableCogenProduct = new RenderableCogenProduct {
    override def render: List[String] = List.empty
    override def renderHeader: List[String] = List.empty
    override def preamble: String = ""
  }
}
