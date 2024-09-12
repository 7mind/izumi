package izumi.fundamentals.collections

import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.Seq

class WildcardPrefixTreeTest extends AnyWordSpec {
  def call[K, V](tree: WildcardPrefixTree[K, V], path: K*): Set[V] = {
    tree.findAllMatchingSubtrees(path.toList).flatMap(_.allValuesFromSubtrees).toSet
  }

  "prefix tree1" should {
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

      assert(tree.findAllMatchingSubtrees(Seq("a")).size == 2)

      assert(tree.findExactMatch(Seq("a")).exists(_.values.isEmpty))
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

    "find best matches" in {
      val tree1 = WildcardPrefixTree.build(
        Seq(
          (Seq(Some("b"), Some("c")), 1),
          (Seq(Some("b"), Some("c"), Some("c")), 10),
          (Seq(Some("b"), None), 2),
          (Seq(Some("b"), None, Some("c")), 11),
          (Seq(None), 100),
          (Seq(None), 101),
        )
      )

      assert(tree1.findBestMatch(Seq("b", "c", "missing")).found.values.toSet == Set(1))
      assert(tree1.findBestMatch(Seq("b", "c", "missing")).unmatched == Seq("missing"))

      assert(tree1.findBestMatch(Seq("b", "missing", "c")).found.values.toSet == Set(11))
      assert(tree1.findBestMatch(Seq("b", "missing", "c")).unmatched.isEmpty)

      assert(tree1.findBestMatch(Seq("b", "missing", "missing")).found.values.toSet == Set(2))
      assert(tree1.findBestMatch(Seq("b", "missing", "missing2")).unmatched == Seq("missing2"))
      assert(tree1.findBestMatch(Seq("b", "x", "missing")).found.values.toSet == Set(2))
      assert(tree1.findBestMatch(Seq("b", "x", "missing")).unmatched == Seq("missing"))

      assert(tree1.findBestMatch(Seq("missing1", "missing2")).found.values.toSet == Set(100, 101))
      assert(tree1.findBestMatch(Seq("missing1", "missing2")).unmatched == Seq("missing2"))

      val tree2 = WildcardPrefixTree.build(
        Seq(
          (Seq.empty, 100),
          (Seq(Some("x")), 101),
        )
      )

      assert(tree2.findBestMatch(Seq("b", "c", "missing")).found.values.toSet == Set(100))

    }
  }

}
