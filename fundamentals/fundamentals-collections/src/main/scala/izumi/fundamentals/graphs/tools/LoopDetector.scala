package izumi.fundamentals.graphs.tools

import LoopDetector.Cycles
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.collection.mutable
import scala.collection.compat._


trait LoopDetector {
  def findCyclesForNode[T](node: T, graph: IncidenceMatrix[T]): Option[Cycles[T]]

  def findCyclesForNodes[T](nodes: Set[T], graph: IncidenceMatrix[T]): Set[Cycles[T]] = {
    nodes.map(n => findCyclesForNode(n, graph)).collect {
      case Some(c) =>
        c
    }
  }

  def findLoopMember[T](graph: IncidenceMatrix[T]): Option[T]
}

object LoopDetector {

  case class Loop[T](loop: Seq[T]) extends AnyVal

  case class Cycles[T](node: T, loops: Seq[Loop[T]])

  object Impl extends LoopDetector {
    def findCyclesForNode[T](node: T, graph: IncidenceMatrix[T]): Option[Cycles[T]] = {
      val loops = new mutable.HashSet[Loop[T]]()
      traceCycles(graph, loops)(node, Seq.empty, Set.empty)
      loops.toList match {
        case Nil =>
          None
        case l =>
          Some(Cycles(node, l))
      }

    }

    private def traceCycles[T](graph: IncidenceMatrix[T], loops: mutable.HashSet[Loop[T]])(current: T, path: Seq[T], seen: Set[T]): Unit = {
      if (seen.contains(current)) {
        loops.add(Loop(path :+ current))
        ()
      } else {
        graph.links.get(current) match {
          case Some(value) =>
            value.foreach {
              referenced =>
                traceCycles(graph, loops)(referenced, path :+ current, seen + current)
            }
          case None =>
        }
      }
    }

    def findLoopMember[T](graph: IncidenceMatrix[T]): Option[T] = {
      val untested = mutable.HashMap.from(graph.links)

      while (untested.nonEmpty) {
        val seen = new mutable.HashSet[T]()
        detectLoops(untested, seen, List(untested.head._1)) match {
          case Some(inLoop) =>
            return Some(inLoop)
          case None =>
            untested --= seen
        }
      }

      None
    }

    @scala.annotation.tailrec
    private def detectLoops[T](untested: mutable.HashMap[T, Set[T]], seen: mutable.HashSet[T], start: Seq[T]): Option[T] = {
      start.find(seen.contains) match {
        case Some(value) =>
          Some(value)
        case None =>
          seen ++= start
          val toTest = start.flatMap { a: T =>
            untested.getOrElse(a, Seq.empty)
          }
          if (toTest.isEmpty) {
            None
          } else {
            println((untested, seen, toTest))
            detectLoops(untested, seen, toTest)
          }
      }
    }
  }

}