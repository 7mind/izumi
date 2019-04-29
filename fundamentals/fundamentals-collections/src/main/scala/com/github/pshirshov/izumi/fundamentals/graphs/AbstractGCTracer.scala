package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec
import scala.collection.mutable

trait AbstractGCTracer[NodeId, Node] {
  case class Pruned(nodes: Vector[Node], reachable: Set[NodeId]) {
    @inline
    def prune: Pruned = {
      val filteredNodes = nodes.filter(s => reachable.contains(id(s)))
      Pruned(filteredNodes, reachable)
    }
  }

  final def gc(elements: Vector[Node]): Pruned = {
    val reachable = mutable.HashSet[NodeId]()
    val mapping = elements.map(e => id(e) -> e).toMap
    val roots = mapping.keySet.filter(isRoot)
    reachable ++= roots

    trace(mapping, roots, reachable)

    prePrune(Pruned(elements, reachable.toSet)).prune
  }

  @tailrec
  private def trace(index: Map[NodeId, Node], toTrace: Set[NodeId], reachable: mutable.HashSet[NodeId]): Unit = {
    val newDeps = toTrace
      .map(index.apply)
      .flatMap(extractDependencies(index, _))
      .diff(reachable)

    if (newDeps.nonEmpty) {
      reachable ++= newDeps
      trace(index, newDeps, reachable)
    }
  }

  protected def prePrune(pruned: Pruned): Pruned

  @inline
  protected def extractDependencies(index: Map[NodeId, Node], node: Node): Set[NodeId]

  protected def isRoot(node: NodeId): Boolean

  protected def id(node: Node): NodeId
}
