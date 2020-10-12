package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphImpl.DirectedGraphPred
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.gc.GC
import izumi.fundamentals.graphs.tools.gc.GC.GCInput
import izumi.fundamentals.graphs.tools.mutations.MutationResolver.WeakEdge

trait GraphSyntax[G[n, m] <: AbstractGraph[n, m]] {

  protected def unsafeFactory[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): G[N, M]

  implicit class DGExt[N, M](g: AbstractGraph[N, M] with DirectedGraphPred[N, M]) {
    def gc(roots: Set[N], weak: Set[WeakEdge[N]]): Either[Nothing, G[N, M]] = {
      for {
        collected <- new GC.GCTracer[N].collect(GCInput(g.predcessors, roots, weak))
      } yield {
        unsafeFactory(collected.predcessorMatrix, g.meta.without(collected.removed))
      }

    }
  }

}
