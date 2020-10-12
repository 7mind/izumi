package izumi.fundamentals.graphs.tools.cycles

import izumi.fundamentals.graphs.DAGError
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.nowarn
import scala.collection.mutable

// TODO: this class is not required for distage
@nowarn("msg=Unused import")
final class CycleEraser[N](predcessorsMatrix: IncidenceMatrix[N], breaker: LoopBreaker[N]) {
  import scala.collection.compat._

  private val output: mutable.Map[N, mutable.LinkedHashSet[N]] = mutable.HashMap.empty
  private var current: mutable.Map[N, mutable.LinkedHashSet[N]] = asMut(predcessorsMatrix)

  def run(): Either[DAGError[N], IncidenceMatrix[N]] = {
    val (noPreds, hasPreds) = current.partition(_._2.isEmpty)

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        Right(IncidenceMatrix(output.toSeq: _*))
      } else {
        val asMatrix = IncidenceMatrix(hasPreds.view.mapValues(_.toSet).toMap)

        for {
          noLoops <- breaker.breakLoops(asMatrix).left.map(_ => DAGError.UnexpectedLoops[N]())
          verified <- verifyNoLoops(noLoops)
          result <- {
            current = asMut(verified)
            run()
          }
        } yield result
      }

    } else {
      val found = noPreds.keySet

      noPreds.foreach {
        case (s, p) =>
          output.getOrElseUpdate(s, mutable.LinkedHashSet.empty[N]) ++= p
      }
//      hasPreds.mapValuesInPlace {
//        (s, p) =>
//          val links = p.intersect(found)
//          output.getOrElseUpdate(s, mutable.LinkedHashSet.empty[N]) ++= links
//          p.diff(found)
//      }
//      current = hasPreds

      current.clear()
      current ++= hasPreds.toSeq.map {
        case (s, p) =>
          val links = p.intersect(found)
          output.getOrElseUpdate(s, mutable.LinkedHashSet.empty[N]) ++= links
          (s, p.diff(found))
      }

      run()
    }
  }

  private def verifyNoLoops(noLoops: IncidenceMatrix[N]): Either[DAGError[N], IncidenceMatrix[N]] = {
    LoopDetector.Impl.findLoopMember(noLoops) match {
      case Some(value) =>
        Left(DAGError.LoopBreakerFailed(value))
      case None =>
        Right(noLoops)
    }
  }

  private def asMut(matrix: IncidenceMatrix[N]): mutable.Map[N, mutable.LinkedHashSet[N]] = {
    mutable.HashMap.from(matrix.links.view.mapValues(_.to(mutable.LinkedHashSet)))
  }
}
