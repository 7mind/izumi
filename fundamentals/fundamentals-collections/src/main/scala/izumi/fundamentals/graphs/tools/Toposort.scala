package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.ToposortError
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.{nowarn, tailrec}

final class Toposort {

  def cycleBreaking[T](predcessors: IncidenceMatrix[T], break: ToposortLoopBreaker[T]): Either[ToposortError[T], Seq[T]] = {
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
          resolved <- break.onLoop(done, hasPreds)
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

}
