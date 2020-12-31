package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.DirectedGraphPred
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.gc.GC
import izumi.fundamentals.graphs.tools.gc.GC.GCInput

trait GraphSyntax[G[n, m] <: AbstractGraph[n, m]] {

  protected def unsafeFactory[N, M](predecessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): G[N, M]

  implicit class DGExt[N, M](g: AbstractGraph[N, M] with DirectedGraphPred[N, M]) {
    def gc(roots: Set[N], weak: Set[WeakEdge[N]]): Either[Nothing, G[N, M]] = {
      for {
        collected <- new GC.GCTracer[N].collect(GCInput(g.predecessors, roots, weak))
      } yield {
        unsafeFactory(collected.predecessorMatrix, g.meta.without(collected.removed))
      }

    }
  }

}
