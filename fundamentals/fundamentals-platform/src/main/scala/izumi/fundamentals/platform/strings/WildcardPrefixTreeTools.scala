package izumi.fundamentals.platform.strings

import izumi.fundamentals.collections.WildcardPrefixTree
import izumi.fundamentals.collections.WildcardPrefixTree.PathElement

object WildcardPrefixTreeTools {
  implicit class WildcardPrefixTreeExt[K, V](tree: WildcardPrefixTree[K, V]) {
    def print: String = {
      print(Seq.empty, tree)
    }

    private def print(path: Seq[PathElement[K]], tree: WildcardPrefixTree[K, V]): String = {
      import izumi.fundamentals.preamble.*

      val pathRepr = if (path.isEmpty) {
        "/"
      } else {
        path.map {
          case PathElement.Value(value) => value.toString
          case PathElement.Wildcard => "*"
        }.last
      }

      val head = if (tree.values.isEmpty) {
        s"$pathRepr"
      } else {
        s"$pathRepr => ${tree.values.mkString(", ")}"
      }

      if (tree.children.isEmpty) {
        head
      } else {
        val sub = tree.children.map { case (p, t) => print(path :+ p, t) }.niceList(shift = "").shift(2)
        s"$head $sub"
      }

    }
  }
}
