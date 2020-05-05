package izumi.fundamentals.graphs.deprecated

import scala.annotation.tailrec
import scala.collection.mutable

trait AbstractGCTracer[NodeId, Node] {

  case class Pruned(nodes: Vector[Node], reachable: Set[NodeId])

  final def gc(elements: Vector[Node]): Pruned = {
    val reachable = mutable.HashSet[NodeId]()
    val mapping = elements.map(e => id(e) -> e).toMap
    val roots = mapping.keySet.filter(isRoot)
    reachable ++= roots

    trace(mapping, roots, reachable)
    prune(prePrune(Pruned(elements, reachable.toSet)))
  }

  @inline
  private def prune(pruned: Pruned): Pruned = {
    val filteredNodes = pruned.nodes.filter(s => pruned.reachable.contains(id(s)))
    Pruned(filteredNodes, pruned.reachable)
  }

  @tailrec
  private def trace(index: Map[NodeId, Node], toTrace: Set[NodeId], reachable: mutable.HashSet[NodeId]): Unit = {
    val newDeps = toTrace
      .flatMap {
        id =>
          if (ignoreMissingDeps) {
            index.get(id).toSeq
          } else {
            Seq(index(id))
          }
      }
      .flatMap(extractDependencies(index, _))
      .diff(reachable)

    if (newDeps.nonEmpty) {
      reachable ++= newDeps
      trace(index, newDeps, reachable)
    }
  }

  protected def ignoreMissingDeps: Boolean

  protected def prePrune(pruned: Pruned): Pruned

  protected def extractDependencies(index: Map[NodeId, Node], node: Node): Set[NodeId]

  protected def isRoot(node: NodeId): Boolean

  protected def id(node: Node): NodeId
}
