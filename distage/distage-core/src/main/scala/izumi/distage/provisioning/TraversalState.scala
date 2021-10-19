package izumi.distage.provisioning

import izumi.distage.model.exceptions.ProvisionerIssue
import izumi.distage.model.provisioning.{NewObjectOp, OpStatus}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.nowarn
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

final class TraversalState(
  val current: TraversalState.Current,
  val preds: IncidenceMatrix[DIKey],
  val knownBroken: Set[DIKey],
  val failures: Vector[ProvisionerIssue],
  _status: mutable.HashMap[DIKey, OpStatus],
) {
  def status(): Map[DIKey, OpStatus] = _status.toMap

  @nowarn("msg=Unused import")
  def next(finished: List[TimedFinalResult.Success], issues: List[TimedFinalResult.Failure]): TraversalState = {
    import scala.collection.compat.*

    val nextPreds = preds.without(finished.map(_.key).toSet)

    val broken = issues.map(_.key).toSet
    val currentBroken = knownBroken ++ broken
    val withBrokenDeps = nextPreds.links.filter(_._2.intersect(currentBroken).nonEmpty)
    val newBroken = currentBroken ++ withBrokenDeps.keySet

    val newFailures = failures ++ issues.flatMap(_.issues)
    _status ++= finished.map(k => (k.key, OpStatus.Success(k.time)))
    _status ++= issues.map(k => (k.key, OpStatus.Failure(k.issues, k.time)))
    assert(issues.map(_.key).distinct.size == issues.size)

    val (current, next) = nextPreds.links.view
      .filterKeys(k => !newBroken.contains(k))
      .partition(_._2.isEmpty)

    val step = if (current.isEmpty) {
      if (next.isEmpty) {
        TraversalState.Done()
      } else {
        TraversalState.CannotProgress(nextPreds)
      }
    } else {
      TraversalState.Step(current.map(_._1).toSet)
    }

    new TraversalState(
      step,
      nextPreds,
      newBroken,
      newFailures,
      _status,
    )
  }
}

object TraversalState {
  def apply(preds: IncidenceMatrix[DIKey]): TraversalState = {
    val todo = mutable.HashMap[DIKey, OpStatus]()
    todo ++= preds.links.keySet.map(k => (k, OpStatus.Planned()))
    new TraversalState(
      Step(Set.empty),
      preds,
      Set.empty,
      Vector.empty,
      todo,
    )
  }

  sealed trait Current
  case class Step(steps: collection.Set[DIKey]) extends Current
  case class Done() extends Current
  case class CannotProgress(left: IncidenceMatrix[DIKey]) extends Current
}
