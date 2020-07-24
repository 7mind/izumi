package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.struct.IncidenceMatrix

sealed trait GraphImpl[N, +M] { this: AbstractGraph[N, M] => }

object GraphImpl {

  trait DirectedGraphSucc[N, +M] extends GraphImpl[N, M] { this: AbstractGraph[N, M] =>
    def successors: IncidenceMatrix[N]
  }

  trait DirectedGraphPred[N, +M] extends GraphImpl[N, M] { this: AbstractGraph[N, M] =>
    def predcessors: IncidenceMatrix[N]
  }

}
