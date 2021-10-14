package izumi.distage.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import izumi.fundamentals.collections.IzCollections._

sealed trait OpStatus
object OpStatus {
  case class Planned() extends OpStatus
  case class Success(time: FiniteDuration) extends OpStatus
  case class Failure(issues: List[ProvisionerIssue], time: FiniteDuration) extends OpStatus
}

case class TraversalState(
  preds: IncidenceMatrix[DIKey],
  knownBroken: Set[DIKey],
  failures: mutable.ArrayBuffer[ProvisionerIssue],
  status: mutable.HashMap[DIKey, OpStatus],
) {
  def current(): TraversalState.Current = {
    val (current, next) = preds.links.partition(_._2.isEmpty)
    if (current.isEmpty) {
      if (next.isEmpty) {
        TraversalState.Done()
      } else {
        TraversalState.CannotProgress(preds)
      }
    } else {
      TraversalState.Step(current.keySet)
    }
  }

  def next(finished: Set[DIKey], issues: List[ProvisionerIssue]): TraversalState = {
    val broken = issues.map(_.key).toSet
    val currentBroken = knownBroken ++ broken
    val withBrokenDeps = preds.links.filter(_._2.intersect(currentBroken).nonEmpty)
    val newBroken = currentBroken ++ withBrokenDeps.keySet

    failures ++= issues
    status ++= finished.map(k => (k, OpStatus.Success(FiniteDuration(0, TimeUnit.SECONDS))))
    status ++= issues.map(i => (i.key, i)).toMultimapView.mapValues(issues => OpStatus.Failure(issues.toList, FiniteDuration(0, TimeUnit.SECONDS)))

    TraversalState(
      preds.without(finished ++ newBroken),
      newBroken,
      failures,
      status,
    )
  }
}

object TraversalState {
  def apply(preds: IncidenceMatrix[DIKey]): TraversalState = TraversalState(
    preds,
    Set.empty,
    mutable.ArrayBuffer.empty,
    preds.links.keySet.map(k => (k, OpStatus.Planned())).to(mutable.HashMap),
  )

  sealed trait Current
  case class Step(steps: Set[DIKey]) extends Current
  case class Done() extends Current
  case class CannotProgress(left: IncidenceMatrix[DIKey]) extends Current
}
