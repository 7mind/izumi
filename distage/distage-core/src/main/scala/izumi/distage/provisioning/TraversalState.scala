package izumi.distage.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.provisioning.{NewObjectOp, OpStatus}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

sealed trait TimedFinalResult {
  def isSuccess: Boolean
  def key: DIKey
  def time: FiniteDuration
}
object TimedFinalResult {
  case class Success(key: DIKey, time: FiniteDuration) extends TimedFinalResult {
    override def isSuccess: Boolean = true
  }

  case class Failure(key: DIKey, issues: List[ProvisionerIssue], time: FiniteDuration) extends TimedFinalResult {
    override def isSuccess: Boolean = false
  }
}
sealed trait TimedResult {
  def isSuccess: Boolean
  def key: DIKey
  def time: FiniteDuration
}
object TimedResult {
  case class Success(key: DIKey, ops: Seq[NewObjectOp], time: FiniteDuration) extends TimedResult {
    override def isSuccess: Boolean = true
  }
  case class Failure(key: DIKey, issues: ProvisionerIssue, time: FiniteDuration) extends TimedResult {
    override def isSuccess: Boolean = false
    def toFinal: TimedFinalResult.Failure = TimedFinalResult.Failure(key, List(issues), time)

  }
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

  def next(finished: List[TimedFinalResult.Success], issues: List[TimedFinalResult.Failure]): TraversalState = {
    val broken = issues.map(_.key).toSet
    val currentBroken = knownBroken ++ broken
    val withBrokenDeps = preds.links.filter(_._2.intersect(currentBroken).nonEmpty)
    val newBroken = currentBroken ++ withBrokenDeps.keySet

    failures ++= issues.flatMap(_.issues)
    status ++= finished.map(k => (k.key, OpStatus.Success(k.time)))
    status ++= issues.map(k => (k.key, OpStatus.Failure(k.issues, k.time)))
    assert(issues.map(_.key).distinct.size == issues.size)

    TraversalState(
      preds.without(finished.map(_.key).toSet ++ newBroken),
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
