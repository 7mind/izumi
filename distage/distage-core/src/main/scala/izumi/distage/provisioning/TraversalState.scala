package izumi.distage.provisioning

import izumi.distage.model.reflection.DIKey

case class TraversalState(preds: Map[DIKey, Set[DIKey]]) {
  def next(broken: Set[DIKey]): TraversalState.Next = {
    val (current, next) = preds.partition(_._2.isEmpty)

    if (current.isEmpty) {
      if (next.isEmpty) {
        TraversalState.Done()
      } else {
        TraversalState.Problem(preds)
      }
    } else {
      val allCurrentKeys = current.keySet
      val withoutBrokenDeps = current.filter(_._2.intersect(broken).isEmpty)
      val currentKeys = withoutBrokenDeps.keySet
      TraversalState.Step(TraversalState(preds.removedAll(allCurrentKeys)), currentKeys)
    }
  }
}

object TraversalState {
  sealed trait Next
  case class Step(next: TraversalState, steps: Set[DIKey]) extends Next
  case class Done() extends Next
  case class Problem(left: Map[DIKey, Set[DIKey]]) extends Next
}
