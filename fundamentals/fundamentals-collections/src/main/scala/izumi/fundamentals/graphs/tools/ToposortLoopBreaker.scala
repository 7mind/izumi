package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.ToposortError.{InconsistentInput, UnexpectedLoop}
import ToposortLoopBreaker.ResolvedLoop
import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.graphs.struct.AdjacencyList
import izumi.fundamentals.graphs.tools.cycles.LoopDetector

trait ToposortLoopBreaker[T] {
  def onLoop(done: Seq[T], loopMembers: Map[T, Set[T]]): Either[ToposortError[T], ResolvedLoop[T]]
}

object ToposortLoopBreaker {
  final case class ResolvedLoop[T](breakAt: Set[T]) extends AnyVal

  def dontBreak[T]: ToposortLoopBreaker[T] = (done, hasPreds) => Left(UnexpectedLoop(done, AdjacencyList(hasPreds)))

  def breakOn[T](select: Set[T] => Option[T]): ToposortLoopBreaker[T] = new SingleElementBreaker[T] {
    override def find(done: Seq[T], hasPreds: Map[T, Set[T]]): Option[T] = select(hasPreds.keySet)
  }

  abstract class SingleElementBreaker[T]() extends ToposortLoopBreaker[T] {

    def find(done: Seq[T], hasPreds: Map[T, Set[T]]): Option[T]

    override final def onLoop(done: Seq[T], hasPreds: Map[T, Set[T]]): Either[ToposortError[T], ResolvedLoop[T]] = {
      val loopMembers = hasPreds.view.filterKeys(LoopDetector.isInvolvedIntoCycle(hasPreds)).toMap
      if (loopMembers.nonEmpty) {
        find(done, loopMembers) match {
          case Some(breakLoopAt) =>
            val found = Set(breakLoopAt)
            Right(ResolvedLoop(found))
          case None =>
            Left(UnexpectedLoop(done, AdjacencyList(loopMembers)))
        }
      } else {
        Left(InconsistentInput(AdjacencyList(hasPreds)))
      }
    }
  }

}
