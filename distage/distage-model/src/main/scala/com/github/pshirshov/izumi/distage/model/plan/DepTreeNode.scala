package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

trait DepTreeNode {
  def level: Int

  protected def prefix: String = " " * 2 * level
}

final case class Truncated(level: Int) extends DepTreeNode {
  override def toString: String = prefix + "...\n"
}

final case class CircularReference(key: DIKey, level: Int) extends DepTreeNode {
  override def toString: String = {
    prefix + s"⥀ $level: " + key + "\n"
  }
}

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

  /**
    * Unsafe to call on big graphs
    */
  override def toString: String = {
    val sb = new mutable.StringBuilder()
    val symbol = if (graph.kind == DependencyKind.Depends) {
      "⮑"
    } else {
      "↖"
    }

    if (level > 0) {
      sb.append(prefix)
      sb.append(s"$symbol $level: $key")
    } else {
      sb.append(s"➤ $key")
    }
    sb.append("\n")

    children.foreach {
      c =>
        sb.append(c.toString)
    }

    sb.toString()
  }
}
