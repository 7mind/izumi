package izumi.fundamentals.graphs

import GraphImpl.DirectedGraphPred
import izumi.fundamentals.graphs.tools.GC.{GCInput, WeakEdge}
import izumi.fundamentals.graphs.GraphImpl.DirectedGraphPred
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.GC

trait GraphSyntax[G[_, _] <: AbstractGraph[_, _]] {

  protected def unsafeFactory[N, M](predcessors: IncidenceMatrix[N], meta: GraphMeta[N, M]): G[N, M]

  implicit class DGExt[N, M](g: AbstractGraph[N, M] with DirectedGraphPred[N, M]) {
    def gc(roots: Set[N], weak: Set[WeakEdge[N]]): Either[GCError[N], G[N, M]] = {
      for {
        collected <- new GC.GCTracer[N].collect(GCInput(g.predcessors, roots, weak))
      } yield {
        unsafeFactory(collected.predcessorMatrix, g.meta.without(collected.removed))
      }

    }
  }

}
