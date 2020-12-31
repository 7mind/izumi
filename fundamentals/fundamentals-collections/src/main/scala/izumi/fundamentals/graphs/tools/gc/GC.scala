package izumi.fundamentals.graphs.tools.gc

import izumi.fundamentals.graphs.WeakEdge
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.gc.GC.{GCInput, GCOutput}

// TODO: this class is not required for distage
trait GC[N] {
  def collect(input: GCInput[N]): Either[Nothing, GCOutput[N]]
}

object GC {
  final case class GCInput[N](predecessorMatrix: IncidenceMatrix[N], roots: Set[N], weakSP: Set[WeakEdge[N]])

  final case class GCOutput[N](predecessorMatrix: IncidenceMatrix[N], removed: Set[N])

  class GCTracer[N] extends GC[N] {
    override def collect(input: GCInput[N]): Either[Nothing, GCOutput[N]] = {
      val missingRoots = input.roots.diff(input.predecessorMatrix.links.keySet)
      val withRoots = IncidenceMatrix(input.predecessorMatrix.links ++ missingRoots.map(r => (r, Set.empty[N])))
      val reachable = new Tracer[N].trace(withRoots, input.weakSP.map(w => (w.successor, w.predecessor)), input.roots)
      val unreachable = withRoots.links.keySet.diff(reachable)
      Right(GCOutput(withRoots.without(unreachable), unreachable))

      //      if (missingRoots.isEmpty) {
//      } else {
//        Left(GCError.MissingRoots(missingRoots))
//      }
    }

  }
}
