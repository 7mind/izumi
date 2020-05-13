package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.GraphTraversalError.UnrecoverableLoops
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.GC.WeakEdge
import izumi.fundamentals.graphs.tools.LoopDetector.{Cycles, Impl, Loop}
import izumi.fundamentals.graphs.tools.MutationResolver.AxisPoint
import izumi.fundamentals.graphs.tools.{LoopBreaker, LoopDetector, MutationResolver}
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.compat._

class GraphsTest extends AnyWordSpec {

  import GraphFixtures._

  "Incidense matrix" should {
    "support transposition" in {
      val transposed = directed.transposed
      val maybeOriginal = transposed.transposed
      assert(maybeOriginal == directed)

      assert(cyclic.transposed == cyclic)
    }
  }

  "Loop detector" should {
    "extract loops" in {
      val detector = Impl
      assert(detector.findCyclesForNode(2, directed).contains(Cycles(2, List(Loop(List(2, 1, 2))))))
      assert(detector.findCyclesForNode(1, directed).contains(Cycles(1, List(Loop(List(1, 2, 1))))))
      assert(detector.findCyclesForNode(6, directed).contains(Cycles(6, List(Loop(List(6, 5, 6)), Loop(List(6, 1, 2, 1))))))
      assert(detector.findCyclesForNode(6, directed).to(Set) == detector.findCyclesForNodes(Set(6), directed))

    }

    "detect loops" in {
      val detector = LoopDetector.Impl
      assert(detector.findLoopMember(directed).isDefined)
    }
  }

  "GC" should {
    "remove unreachable nodes" in {
      val dagOut = for {
        g <- DAG.fromSucc(collectableDag, GraphMeta(Map.empty[Int, String]))
        c <- g.gc(Set(6), Set.empty)
      } yield {
        c
      }

      assert(dagOut.map(_.successors) == Right(collectedDag))

      val cyclicOut = for {
        g <- Right(DG.fromSucc(collectableCyclic, GraphMeta(Map.empty[Int, String])))
        c <- g.gc(Set(6), Set.empty)
      } yield {
        c
      }

      assert(cyclicOut.map(_.successors) == Right(collectedCyclic))
    }

    "support weak edges" in {
      val out = for {
        g <- Right(DG.fromSucc(collectableLinear, GraphMeta(Map.empty[Int, String])))
        c <- g.gc(Set(6), Set(WeakEdge(2, 3)))
      } yield {
        c
      }

      assert(
        out.map(_.successors) == Right(
          IncidenceMatrix(
            3 -> Set(4),
            4 -> Set(5),
            5 -> Set(6),
          )
        )
      )

    }
  }

  "DAG" should {
    "not break on acyclic matrices" in {
      assert(DAG.fromSucc(dag, GraphMeta.empty).map {
        d: DAG[Int, String] => d.successors
      } == Right(dag))
      assert(DAG.fromSucc(acyclic, GraphMeta.empty).map {
        d: DAG[Int, Nothing] => d.successors
      } == Right(acyclic))
    }

    "break on cyclic matrices" in {
      assert(DAG.fromSucc(cyclic, GraphMeta.empty).isLeft)
    }

    "detect broken loop breakers" in {
      val brokenBreaker = new LoopBreaker[Int] {
        override def breakLoops(withLoops: IncidenceMatrix[Int]): Either[UnrecoverableLoops[Int], IncidenceMatrix[Int]] = Right(withLoops)
      }
      assert(DAG.fromSucc(cyclic, GraphMeta.empty, brokenBreaker).isLeft)
    }

    "support loop breakers" in {
      val breaker = new LoopBreaker[Int] {
        override def breakLoops(withLoops: IncidenceMatrix[Int]): Either[UnrecoverableLoops[Int], IncidenceMatrix[Int]] = {
          Right(acyclic.transposed)
        }
      }
      assert(DAG.fromSucc(cyclic, GraphMeta.empty, breaker).map {
        d: DAG[Int, Nothing] => d.successors
      } == Right(acyclic))
    }
  }

  "Conflict Resolver" should {
    "resolve standard samples" in {
      val cases = Seq(
        (mutators, Set(AxisPoint("test", "prod")), true, Set("app")),
        (mutators, Set.empty[AxisPoint], false, Set("app")),
        (withLoop, Set(AxisPoint("test", "prod")), true, Set("app")),
        (withLoop, Set.empty[AxisPoint], false, Set("app")),
        (complexMutators, Set.empty[AxisPoint], true, Set("app")),
      )

      val resolver = new MutationResolver.MutationResolverImpl[String, Int, Int]

      for (((f, a, good, roots), idx) <- cases.zipWithIndex) {
        val result = resolver.resolve(f, roots, a, Set.empty)
        if (good) {
          assert(result.isRight, s"positive check #$idx failed")
        } else {
          assert(result.isLeft, s"negative check #$idx failed")
        }
      }
    }
  }
}
