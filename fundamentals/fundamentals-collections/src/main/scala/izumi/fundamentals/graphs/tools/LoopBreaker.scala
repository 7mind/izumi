package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
import izumi.fundamentals.graphs.struct.IncidenceMatrix

trait LoopBreaker[N] {
  def breakLoops(withLoops: IncidenceMatrix[N]): Either[UnrecoverableLoops[N], IncidenceMatrix[N]]
}

object LoopBreaker {

  def terminating[N]: LoopBreaker[N] = new LoopBreaker[N] {
    override def breakLoops(withLoops: IncidenceMatrix[N]): Either[UnrecoverableLoops[N], IncidenceMatrix[N]] = {
      Left(UnrecoverableLoops())
    }
  }
}