package izumi.fundamentals.graphs.traverse

import izumi.fundamentals.graphs.traverse.DAGTraverser.TraverseState

trait TraverseStrategy[F[_], Node, Trace, Progress] {
  def mark(n: Node): MPromise[F, Progress, Trace]

  def isGreen(node: Node, mark: Trace): Boolean

  def canContinue(state: TraverseState[F, Node, Trace, Progress]): Boolean
}
