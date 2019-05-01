package com.github.pshirshov.izumi.fundamentals.collections

import com.github.pshirshov.izumi.fundamentals.graphs.AbstractGCTracer
import org.scalatest.WordSpec

class AbstractDIGcTest extends WordSpec {

  "abstract GC" should {
    "not loop forever on circular dependencies" in {
      import AbstractDIGcTest._
      val gc = new TestGCTracer(false)

      val graph = Vector(
        Node(NodeId("root:1"), Set(NodeId("2"))),
        Node(NodeId("2"), Set(NodeId("3"), NodeId("4"))),
        Node(NodeId("3"), Set(NodeId("2"))),
        Node(NodeId("4"), Set(NodeId("4"))),
        Node(NodeId("5"), Set(NodeId("5"))),
      )
      val result = gc.gc(graph)

      assert(result.reachable == Set(NodeId("root:1"), NodeId("2"), NodeId("3"), NodeId("4")))
    }

    "ignore missing dependencies when expected to" in {
      import AbstractDIGcTest._
      val gc1 = new TestGCTracer(true)

      val graph = Vector(
        Node(NodeId("root:1"), Set(NodeId("2"))),
      )
      val result = gc1.gc(graph)

      assert(result.reachable == Set(NodeId("root:1"), NodeId("2")))

      val gc2 = new TestGCTracer(false)
      intercept[java.util.NoSuchElementException] {
        gc2.gc(graph)
      }
    }
  }
}

object AbstractDIGcTest {

  case class NodeId(id: String)

  case class Node(id: NodeId, deps: Set[NodeId])


  class TestGCTracer(override val ignoreMissing: Boolean) extends AbstractGCTracer[NodeId, Node]{


    override protected def prePrune(pruned: Pruned): Pruned = {
      pruned
    }

    override protected def extractDependencies(index: Map[NodeId, Node], node: Node): Set[NodeId] = {
      node.deps
    }

    override protected def isRoot(node: NodeId): Boolean = node.id.startsWith("root:")

    override protected def id(node: Node): NodeId = node.id
  }
}
