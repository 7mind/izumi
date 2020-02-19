package izumi.fundamentals.collections

import org.scalatest.wordspec.AnyWordSpec


class WildcardPrefixTreeTest extends AnyWordSpec {
  def call[K, V](tree: WildcardPrefixTree[K, V], path: K*): Set[V] = {
    tree.findSubtrees(path.toList).flatMap(_.subtreeValues).toSet
  }

  "prefix tree" should {
    "support prefix search" in {
      val tree = WildcardPrefixTree.build(Seq(
        (Seq(Some("a"), Some("b"), Some("c")), 1),
        (Seq(Some("a"), Some("b")), 2),
        (Seq(Some("a"), Some("b"), Some("d")), 3)
      ))

      assert(call(tree, "a") == Set(1, 2, 3))
      assert(call(tree, "a", "b") == Set(1, 2, 3))
      assert(call(tree, "a", "b", "c") == Set(1))
      assert(call(tree) == Set(1, 2, 3))

      assert(call(tree, "x", "y", "z").isEmpty)

      assert(call(tree, "a", "b", "c", "d").isEmpty)
      assert(call(tree, "a", "b", "x", "d").isEmpty)
    }

    "support wildcards search" in {
      val tree = WildcardPrefixTree.build(Seq(
        (Seq(Some("a"), None, Some("c")), 1),
        (Seq(Some("a"), None, Some("d")), 3),
        (Seq(Some("a"), Some("b")), 2)
      ))

      assert(call(tree, "a") == Set(1, 2, 3))
      assert(call(tree, "a", "b") == Set(1, 2, 3))
      assert(call(tree, "a", "x") == Set(1, 3))
    }
  }

}
