package izumi.fundamentals.graphs.tools

import GC.{GCInput, GCOutput}
import izumi.fundamentals.graphs.GCError
import izumi.fundamentals.graphs.struct.IncidenceMatrix

trait GC[N] {
  def collect(input: GCInput[N]): Either[GCError[N], GCOutput[N]]
}

object GC {
  final case class WeakEdge[N](predcessor: N, successor: N)
  final case class GCInput[N](predcessorMatrix: IncidenceMatrix[N], roots: Set[N], weakSP: Set[WeakEdge[N]])

  final case class GCOutput[N](predcessorMatrix: IncidenceMatrix[N], removed: Set[N])



  class GCTracer[N] extends GC[N] {
    override def collect(input: GCInput[N]): Either[GCError[N], GCOutput[N]] = {
      val missingRoots = input.roots.diff(input.predcessorMatrix.links.keySet)
      if (missingRoots.isEmpty) {
        val reachable = new Tracer[N].trace(input.predcessorMatrix, input.weakSP.map(w => (w.successor, w.predcessor)), input.roots)
        val unreachable = input.predcessorMatrix.links.keySet.diff(reachable)
        Right(GCOutput(input.predcessorMatrix.without(unreachable), unreachable))
      } else {
        Left(GCError.MissingRoots(missingRoots))
      }
    }

  }
}








