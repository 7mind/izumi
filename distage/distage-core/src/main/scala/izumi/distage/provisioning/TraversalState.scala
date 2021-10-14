package izumi.distage.provisioning

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

case class TraversalState(preds: IncidenceMatrix[DIKey]) {
  def next(broken: Set[DIKey]): TraversalState.Next = {
    val (current, next) = preds.links.partition(_._2.isEmpty)

    if (current.isEmpty) {
      if (next.isEmpty) {
        TraversalState.Done()
      } else {
        TraversalState.CannotProgress(preds)
      }
    } else {
      val allCurrentKeys = current.keySet
      val withoutBrokenDeps = current.filter(_._2.intersect(broken).isEmpty)
      val currentKeys = withoutBrokenDeps.keySet
      TraversalState.Step(TraversalState(preds.without(allCurrentKeys)), currentKeys)
    }
  }
}

object TraversalState {
  sealed trait Next
  case class Step(next: TraversalState, steps: Set[DIKey]) extends Next
  case class Done() extends Next
  case class CannotProgress(left: IncidenceMatrix[DIKey]) extends Next
}
