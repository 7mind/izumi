package izumi.fundamentals.graphs

trait NodeShow[T] {
  def show(t: T): String
}

object NodeShow {
  implicit def IntNodeShow: NodeShow[Int] = _.toString
  implicit def StringNodeShow: NodeShow[String] = new NodeShow[String] {
    override def show(t: String): String = t
  }
}
