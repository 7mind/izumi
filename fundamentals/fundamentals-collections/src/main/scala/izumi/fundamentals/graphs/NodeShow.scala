package izumi.fundamentals.graphs

trait NodeShow[T] {
  def show(t: T): String
}

object NodeShow {
  implicit object IntNodeShow$ extends NodeShow[Int] {
    override def show(t: Int): String = t.toString
  }
  implicit object StringNodeShow$ extends NodeShow[String] {
    override def show(t: String): String = t
  }
}