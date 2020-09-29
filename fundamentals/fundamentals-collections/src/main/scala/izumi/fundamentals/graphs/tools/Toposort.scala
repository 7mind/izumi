package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.graphs.ToposortError.InconsistentInput
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.{nowarn, tailrec}

object Toposort {

  final def cycleBreaking[T](predcessors: IncidenceMatrix[T], break: ToposortLoopBreaker[T]): Either[ToposortError[T], Seq[T]] = {
    cycleBreaking(predcessors.links, Seq.empty, break)
  }

  @nowarn("msg=Unused import")
  @tailrec
  private[this] def cycleBreaking[T](predcessors: Map[T, Set[T]], done: Seq[T], break: ToposortLoopBreaker[T]): Either[ToposortError[T], Seq[T]] = {
    import scala.collection.compat._
    val (noPreds, hasPreds) = predcessors.partition(_._2.isEmpty)

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        Right(done)
      } else { // circular dependency
        val maybeNext = for {
          loopMembers <- Right(hasPreds.view.filterKeys(isInvolvedIntoCycle(hasPreds)).toMap)
          _ <- if (loopMembers.isEmpty) Left(InconsistentInput(IncidenceMatrix(hasPreds))) else Right(())
          resolved <- break.onLoop(done, loopMembers)
          next = hasPreds.view.filterKeys(k => !resolved.breakAt.contains(k)).mapValues(_ -- resolved.breakAt).toMap

        } yield {
          (resolved.breakAt, next)
        }

        maybeNext match {
          case Right((breakAt, next)) =>
            cycleBreaking(next, done ++ breakAt, break)

          case Left(e) =>
            Left(e)
        }
      }
    } else {
      val found = noPreds.keySet
      val next = hasPreds.view.mapValues(_ -- found).toMap
      cycleBreaking(next, done ++ found, break)
    }
  }

  private[this] def isInvolvedIntoCycle[T](toPreds: Map[T, Set[T]])(key: T): Boolean = {
    test(toPreds, Set.empty, key, key)
  }

  private[this] def test[T](toPreds: Map[T, Set[T]], stack: Set[T], toTest: T, needle: T): Boolean = {
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
