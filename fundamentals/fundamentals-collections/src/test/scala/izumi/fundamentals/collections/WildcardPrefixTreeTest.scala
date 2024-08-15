package izumi.fundamentals.collections

import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.Seq

class WildcardPrefixTreeTest extends AnyWordSpec {
  def call[K, V](tree: WildcardPrefixTree[K, V], path: K*): Set[V] = {
    tree.findSubtrees(path.toList).flatMap(_.allValuesFromSubtrees).toSet
  }

  "prefix tree" should {
    "support prefix search" in {
      val tree = WildcardPrefixTree.build(
        Seq(
          (Seq(Some("a"), Some("b"), Some("c")), 1),
          (Seq(Some("a"), Some("b")), 2),
          (Seq(Some("a"), Some("b"), Some("d")), 3),
        )
      )

      assert(call(tree, "a") == Set(1, 2, 3))
      assert(call(tree, "a", "b") == Set(1, 2, 3))
      assert(call(tree, "a", "b", "c") == Set(1))
      assert(call(tree) == Set(1, 2, 3))

      assert(call(tree, "x", "y", "z").isEmpty)

      assert(call(tree, "a", "b", "c", "d").isEmpty)
      assert(call(tree, "a", "b", "x", "d").isEmpty)
    }

    "support wildcards search" in {
      val tree = WildcardPrefixTree.build(
        Seq(
          (Seq(Some("a"), None, Some("c")), 1),
          (Seq(Some("a"), None, Some("d")), 3),
          (Seq(Some("a"), Some("b")), 2),
        )
      )

      assert(call(tree, "a") == Set(1, 2, 3))
      assert(call(tree, "a", "b") == Set(1, 2, 3))
      assert(call(tree, "a", "x") == Set(1, 3))
    }

    "support root wildcard" in {
      val tree = WildcardPrefixTree.build(
        Seq(
          (Seq(Some("a"), None, Some("c")), 1),
          (Seq(Some("a"), None, Some("d")), 3),
          (Seq(None), 2),
        )
      )

      assert(call(tree, "a") == Set(1, 2, 3))
      assert(call(tree, "b") == Set(2))

      assert(tree.findSubtrees(Seq("a")).size == 2)

      assert(tree.findSubtree(Seq("a")).exists(_.values.isEmpty))
    }

    "merge values in identical rows" in {
      val tree = WildcardPrefixTree.build(
        Seq(
          (Seq(Some("b"), None, Some("c")), 5),
          (Seq(Some("b"), None, Some("c")), 6),
          (Seq(None), 1),
          (Seq(None), 2),
        )
      )

      assert(call(tree, "a") == Set(1, 2))
      assert(call(tree, "b", "x", "c") == Set(5, 6))
    }
  }

}
