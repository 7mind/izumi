package izumi.logstage.api.rendering.logunits

sealed trait LETree

object LETree {
  final case class Sequence(sub: Seq[LETree]) extends LETree
  final case class ColoredNode(color: String, sub: LETree) extends LETree
  final case class TextNode(text: String) extends LETree
}
