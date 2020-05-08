package izumi.fundamentals.graphs.tools

import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.tailrec
import scala.collection.mutable

class Tracer[N] {
  def trace(matrix: IncidenceMatrix[N], exclusions: Set[(N, N)], toTrace: Set[N]): Set[N] = {
    val reachable = mutable.HashSet[N]()
    reachable ++= toTrace
    trace(matrix, exclusions, toTrace, reachable)
    reachable.toSet
  }

  @tailrec
  private def trace(matrix: IncidenceMatrix[N], exclusions: Set[(N, N)], toTrace: Set[N], reachable: mutable.HashSet[N]): Unit = {
    val nextReachable = toTrace
      .flatMap(s => matrix.links(s).map(p => (s, p)))
      .diff(exclusions)
      .map(_._2)
      .diff(reachable)

    if (nextReachable.nonEmpty) {
      reachable ++= nextReachable
      trace(matrix, exclusions, nextReachable, reachable)
    }
  }
}
