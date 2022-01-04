package izumi.distage.provisioning

import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.provisioning.{NewObjectOp, OpStatus}
import izumi.distage.model.reflection.DIKey
import izumi.distage.provisioning.TraversalState.Current.{CannotProgress, Done, Step}
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
  final case class Success(key: DIKey, time: FiniteDuration) extends TimedFinalResult {
    override def isSuccess: Boolean = true
  }

  final case class Failure(key: DIKey, issues: List[ProvisionerIssue], time: FiniteDuration) extends TimedFinalResult {
    override def isSuccess: Boolean = false
  }
}

sealed trait TimedResult {
  def isSuccess: Boolean
  def key: DIKey
  def time: FiniteDuration
}
object TimedResult {
  final case class Success(key: DIKey, ops: Seq[NewObjectOp], time: FiniteDuration) extends TimedResult {
    override def isSuccess: Boolean = true
  }
  final case class Failure(key: DIKey, issues: ProvisionerIssue, time: FiniteDuration) extends TimedResult {
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

    val nextPreds = preds.without(finished.iterator.map(_.key).toSet)

    val broken = issues.map(_.key).toSet
    assert(broken.size == issues.size)
    val currentBroken = knownBroken ++ broken
    val withBrokenDeps = nextPreds.links.filter(_._2.intersect(currentBroken).nonEmpty)
    val newBroken = currentBroken ++ withBrokenDeps.keySet

    val newFailures = failures ++ issues.flatMap(_.issues)
    _status ++= finished.map(k => (k.key, OpStatus.Success(k.time)))
    _status ++= issues.map(k => (k.key, OpStatus.Failure(k.issues, k.time)))

    val (current, next) = nextPreds.links.view
      .filterKeys(!newBroken.contains(_))
      .partition(_._2.isEmpty)

    val step = if (current.isEmpty) {
      if (next.isEmpty) {
        Done()
      } else {
        CannotProgress(nextPreds)
      }
    } else {
      Step(current.keys)
    }

    new TraversalState(
      current = step,
      preds = nextPreds,
      knownBroken = newBroken,
      failures = newFailures,
      _status = _status,
    )
  }

}

object TraversalState {
  def apply(preds: IncidenceMatrix[DIKey]): TraversalState = {
    val todo = mutable.HashMap[DIKey, OpStatus]()
    todo ++= preds.links.keySet.map(_ -> OpStatus.Planned())
    new TraversalState(
      current = Step(Set.empty),
      preds = preds,
      knownBroken = Set.empty,
      failures = Vector.empty,
      _status = todo,
    )
  }

  sealed trait Current
  object Current {
    final case class Step(steps: Iterable[DIKey]) extends Current
    final case class Done() extends Current
    final case class CannotProgress(left: IncidenceMatrix[DIKey]) extends Current
  }
}
