package izumi.fundamentals.graphs.tools.cycles

import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
import izumi.fundamentals.graphs.struct.AdjacencyList

trait LoopBreaker[N] {
  def breakLoops(withLoops: AdjacencyList[N]): Either[UnrecoverableLoops[N], AdjacencyList[N]]
}

object LoopBreaker {
  def terminating[N]: LoopBreaker[N] = _ => Left(UnrecoverableLoops())
}
