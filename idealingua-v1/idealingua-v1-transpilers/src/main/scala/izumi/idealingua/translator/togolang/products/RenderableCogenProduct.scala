package izumi.idealingua.translator.togolang.products

trait RenderableCogenProduct {
  def render: List[String]
  def renderHeader: List[String]
  def renderTests: List[String]
  def isEmpty: Boolean = render.isEmpty
}

object RenderableCogenProduct {
  def empty: RenderableCogenProduct = new RenderableCogenProduct {
    override def render: List[String] = List.empty
    override def renderHeader: List[String] = List.empty
    override def renderTests: List[String] = List.empty
  }
}
