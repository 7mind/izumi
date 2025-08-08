package izumi.fundamentals.graphs.tools.random

import izumi.fundamentals.graphs.struct.AdjacencyPredList

import scala.annotation.nowarn
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.Random

@nowarn("msg=[Uu]nused import")
object RandomGraph {
  import scala.collection.compat._

  def makeDG[N: Generator: ClassTag](nodes: Int, maxEdges: Int, random: Random = Random): AdjacencyPredList[N] = {
    assert(nodes > 0)
    assert(maxEdges > 0)
    val ordered = makeShuffledNodes(nodes, random)

    val out = new mutable.HashMap[N, Set[N]]()
    for ((n, idx) <- ordered.zipWithIndex) {

      val links = if (idx > 0) {
        (0 until maxEdges).flatMap {
          _ =>
            if (random.nextBoolean()) {
              Seq(ordered(Random.nextInt(ordered.length)))
            } else {
              Seq.empty
            }
        }.toSet
      } else {
        Set.empty[N]
      }
      out.put(n, links)
    }

    AdjacencyPredList(out.toMap)

  }

  def makeDAG[N: Generator: ClassTag](nodes: Int, maxEdges: Int, random: Random = Random): AdjacencyPredList[N] = {
    assert(nodes > 0)
    assert(maxEdges > 0)
    val ordered = makeShuffledNodes(nodes, random)

    val out = new mutable.HashMap[N, Set[N]]()
    for ((n, idx) <- ordered.zipWithIndex) {

      val links = if (idx > 0) {
        (0 until maxEdges)
          .flatMap(
            rndIdx =>
              if (random.nextBoolean()) {
                Seq(ordered(rndIdx % idx))
              } else {
                Seq.empty
              }
          )
          .toSet
      } else {
        Set.empty[N]
      }
      out.put(n, links)
    }

    AdjacencyPredList(out.toMap)
  }

  private def makeShuffledNodes[N: Generator: ClassTag](nodes: Int, random: Random): Array[N] = {
    val gen = implicitly[Generator[N]]
    val initial = (0 until nodes).map(_ => gen.make())
    val toFix = initial.to(mutable.HashSet)
    while (toFix.size < nodes) {
      toFix.add(gen.make())
    }

    val ordered = new Array[N](toFix.size)
    toFix.copyToArray(ordered)

    for (idx <- 0 to ordered.length / 2) {
      val swapWith = random.nextInt(ordered.length)
      val t = ordered(idx)
      ordered.update(idx, ordered(swapWith))
      ordered.update(swapWith, t)
    }
    ordered
  }

}
