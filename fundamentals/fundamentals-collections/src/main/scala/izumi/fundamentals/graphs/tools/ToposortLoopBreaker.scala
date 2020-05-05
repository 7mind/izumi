package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.ToposortError.{InconsistentInput, UnexpectedLoop}
import ToposortLoopBreaker.ResolvedLoop
import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.collection.compat._


trait ToposortLoopBreaker[T] {
  def onLoop(done: Seq[T], loopMembers: Map[T, Set[T]]): Either[ToposortError[T], ResolvedLoop[T]]
}

object ToposortLoopBreaker {
  case class ResolvedLoop[T](breakAt: Set[T]) extends AnyVal

  def dontBreak[T]: ToposortLoopBreaker[T] = new ToposortLoopBreaker[T] {
    override def onLoop(done: Seq[T], hasPreds: Map[T, Set[T]]): Either[ToposortError[T], ResolvedLoop[T]] = {
      Left(UnexpectedLoop(done, IncidenceMatrix(hasPreds)))
    }
  }

  def breakOn[T](select: Set[T] => Option[T]): ToposortLoopBreaker[T] = new SingleElementBreaker[T] {
    override def find(done: Seq[T], loopMembers: Map[T, Set[T]]): Option[T] = select(loopMembers.keySet)
  }

  abstract class SingleElementBreaker[T]() extends ToposortLoopBreaker[T] {

    def find(done: Seq[T], hasPreds: Map[T, Set[T]]): Option[T]

    def onLoop(done: Seq[T], hasPreds: Map[T, Set[T]]): Either[ToposortError[T], ResolvedLoop[T]] = {
      val loopMembers = hasPreds.view.filterKeys(isInvolvedIntoCycle(hasPreds)).toMap
      if (loopMembers.nonEmpty) {
        find(done, loopMembers) match {
          case Some(breakLoopAt) =>
            val found = Set(breakLoopAt)
            Right(ResolvedLoop(found))
          case None =>
            Left(UnexpectedLoop(done, IncidenceMatrix(loopMembers)))
        }
      } else {
        Left(InconsistentInput(IncidenceMatrix(hasPreds)))
      }
    }

    private def isInvolvedIntoCycle(toPreds: Map[T, Set[T]])(key: T): Boolean = {
      test(toPreds, Set.empty, key, key)
    }

    private def test(toPreds: Map[T, Set[T]], stack: Set[T], toTest: T, needle: T): Boolean = {
      val deps = toPreds.getOrElse(toTest, Set.empty)

      if (deps.contains(needle)) {
        true
      } else {
        deps.exists {
          d =>
            if (stack.contains(d)) {
              false
            } else {
              test(toPreds, stack + d, d, needle)
            }
        }
      }
    }
  }

}