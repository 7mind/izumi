package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
import izumi.fundamentals.graphs.struct.{AdjacencyList, AdjacencySuccList}
import izumi.fundamentals.graphs.tools.cycles.LoopDetector.{Cycles, Impl, Loop}
import izumi.fundamentals.graphs.tools.cycles.{LoopBreaker, LoopDetector}
import org.scalatest.wordspec.AnyWordSpec

class GraphsTest extends AnyWordSpec {

  import GraphFixtures._

  "Incidense matrix" should {
    "support transposition" in {
      val transposed = directed.transposed
      val maybeOriginal = transposed.transposed
      assert(maybeOriginal.links == directed.links)

      assert(cyclic.transposed == cyclic)
    }
  }

  "Loop detector" should {
    "extract loops" in {
      val detector = Impl
      assert(detector.findCyclesForNode(2, directed).contains(Cycles(2, List(Loop(List(2, 1, 2))))))
      assert(detector.findCyclesForNode(1, directed).contains(Cycles(1, List(Loop(List(1, 2, 1))))))
      assert(detector.findCyclesForNode(6, directed).contains(Cycles(6, List(Loop(List(6, 5, 6)), Loop(List(6, 1, 2, 1))))))
      assert(detector.findCyclesForNode(6, directed).toSet == detector.findCyclesForNodes(Set(6), directed))

    }

    "detect loops" in {
      val detector = LoopDetector.Impl
      assert(detector.findLoopMember(directed).isDefined)
    }
  }

  "GC" should {
    "remove unreachable nodes" in {
      val dagOut = for {
        g <- DAG.fromSucc(collectableDag.asSucc, GraphMeta(Map.empty[Int, String]))
        c <- g.gc(Set(6), Set.empty)
      } yield {
        c
      }

      assert(dagOut.map(_.successors) == Right(collectedDag.asSucc))

      val cyclicOut = for {
        g <- Right(DG.fromSucc(collectableCyclic.asSucc, GraphMeta(Map.empty[Int, String])))
        c <- g.gc(Set(6), Set.empty)
      } yield {
        c
      }

      assert(cyclicOut.map(_.successors) == Right(collectedCyclic.asSucc))
    }

    "support weak edges" in {
      val out = for {
        g <- Right(DG.fromSucc(collectableLinear.asSucc, GraphMeta(Map.empty[Int, String])))
        c <- g.gc(Set(6), Set(WeakEdge(2, 3)))
      } yield {
        c
      }

      assert(
        out.map(_.successors) == Right(
          AdjacencySuccList(
            3 -> Set(4),
            4 -> Set(5),
            5 -> Set(6),
          )
        )
      )

    }
  }

  "DG" should {
    "support two instantiation ways" in {
      val g1 = DG.fromSucc(dag.asSucc, GraphMeta.empty)
      val g2 = DG.fromPred(dag.asPred, GraphMeta.empty)
      assert(g1.successors.links == g2.predecessors.links)
      assert(g1.predecessors.links == g2.successors.links)
    }
  }

  "DAG" should {
    "support two instantiation ways" in {
      val g1 = DAG.fromSucc(dag.asSucc, GraphMeta.empty).toOption.get
      val g2 = DAG.fromPred(dag.asPred, GraphMeta.empty).toOption.get
      assert(g1.successors.links == g2.predecessors.links)
      assert(g1.predecessors.links == g2.successors.links)
    }

    "not break on acyclic matrices" in {
      assert(DAG.fromSucc(dag.asSucc, GraphMeta.empty).map {
        (d: DAG[Int, String]) => d.successors
      } == Right(dag.asSucc))
      assert(DAG.fromSucc(acyclic.asSucc, GraphMeta.empty).map {
        (d: DAG[Int, Nothing]) => d.successors
      } == Right(acyclic.asSucc))
    }

    "break on cyclic matrices" in {
      assert(DAG.fromSucc(cyclic.asSucc, GraphMeta.empty).isLeft)
    }

    "detect broken loop breakers" in {
      val brokenBreaker = new LoopBreaker[Int] {
        override def breakLoops(withLoops: AdjacencyList[Int]): Either[UnrecoverableLoops[Int], AdjacencyList[Int]] = Right(withLoops)
      }
      assert(DAG.fromSucc(cyclic.asSucc, GraphMeta.empty, brokenBreaker).isLeft)
    }

    "support loop breakers" in {
      val breaker = new LoopBreaker[Int] {
        override def breakLoops(withLoops: AdjacencyList[Int]): Either[UnrecoverableLoops[Int], AdjacencyList[Int]] = {
          Right(acyclic.transposed)
        }
      }
      assert(DAG.fromPred(cyclic.asPred, GraphMeta.empty, breaker).map {
        (d: DAG[Int, Nothing]) => d.successors
      } == Right(acyclic.asSucc))
    }
  }
}
