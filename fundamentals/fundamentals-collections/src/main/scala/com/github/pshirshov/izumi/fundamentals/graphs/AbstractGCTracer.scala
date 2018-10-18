package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec
import scala.collection.mutable

trait AbstractGCTracer[NodeId, Node] {

  case class Result(filtered: Vector[Node], reachable: Set[NodeId])
  case class PreFltered(filtered: Vector[Node], moreReachables: Set[NodeId])

  final def gc(elements: Vector[Node]): Result = {
    val reachable = mutable.HashSet[NodeId]()
    val roots = elements.map(id).filter(isRoot).toSet
    reachable ++= roots
    trace(elements.map(e => id(e) -> e).toMap, roots, reachable)

    val prefiltered = prefilter(reachable.toSet, elements)
    val filtered = prefiltered.filtered.filter(s => prefiltered.moreReachables.contains(id(s)))
    Result(filtered, prefiltered.moreReachables)
  }

  @tailrec
  private def trace(index: Map[NodeId, Node], toTrace: Set[NodeId], reachable: mutable.HashSet[NodeId]): Unit = {
    val newDeps = toTrace
      .map(index.apply)
      .flatMap(extract(index, _))
      .diff(reachable)

    if (newDeps.nonEmpty) {
      reachable ++= newDeps
      trace(index, newDeps, reachable)
    }
  }

  protected def prefilter(traced: Set[NodeId], elements: Vector[Node]): PreFltered

  @inline
  protected def extract(index: Map[NodeId, Node], node: Node): Set[NodeId]

  protected def isRoot(node: NodeId): Boolean

  protected def id(node: Node): NodeId
}
