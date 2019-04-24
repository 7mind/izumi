package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

sealed trait DepTreeNode {
  def level: Int

}

final case class Truncated(level: Int) extends DepTreeNode

final case class CircularReference(key: DIKey, level: Int) extends DepTreeNode

final case class DepNode(key: DIKey, graph: DependencyGraph, level: Int, limit: Option[Int], exclusions: Set[DIKey]) extends DepTreeNode {
  def children: Set[DepTreeNode] = {
    val children = graph.direct(key)

    if (limit.exists(_ <= level)) {
      Set(Truncated(level))
    } else {
      children.diff(exclusions).map {
        child =>
          DepNode(child, graph, level + 1, limit, exclusions + key)
      } ++ children.intersect(exclusions).map {
        child =>
          CircularReference(child, level + 1)
      }
    }
  }
}

object DepNode {
  implicit class DepNodeExt(val node: DepNode) extends AnyVal {
    def render(): String = new DepTreeRenderer(node).render()
  }
}

class DepTreeRenderer(node: DepNode) {
  val minimizer = new KeyMinimizer(collectKeys(node))

  def render(): String = render(node)
  /**
    * May be unsafe to call on big graphs
    */
  private def render(node: DepTreeNode): String = {
    val prefix = " " * 2 * node.level

    node match {
      case _: Truncated =>
        prefix + "...\n"
      case node: CircularReference =>
        s"$prefix⥀ ${node.level}: ${minimizer.render(node.key)}\n"
      case node: DepNode =>
        val sb = new mutable.StringBuilder()
        val symbol = if (node.graph.kind == DependencyKind.Depends) {
          "⮑"
        } else {
          "↖"
        }

        if (node.level > 0) {
          sb.append(prefix)
          sb.append(s"$symbol ${node.level}: ${minimizer.render(node.key)}")
        } else {
          sb.append(s"➤ ${minimizer.render(node.key)}")
        }
        sb.append("\n")

        node.children.foreach {
          c =>
            sb.append(render(c))
        }

        sb.toString()

    }
  }


  private def collectKeys(node: DepTreeNode): Set[DIKey] = {
    node match {
      case _: Truncated =>
        Set.empty
      case r: CircularReference =>
        Set(r.key)
      case n: DepNode =>
        Set(n.key) ++ n.children.flatMap(collectKeys)
    }
  }

}
