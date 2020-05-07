package izumi.fundamentals.collections

trait Loops {
  def findCyclesFor[T](node: T, graph: T => Option[Set[T]]): Seq[Seq[T]] = {
    val loops = new scala.collection.mutable.HashSet[Seq[T]]()
    traceCycles(graph, loops)(node, Seq.empty, Set.empty)
    loops.toSeq
  }

  private def traceCycles[T](graph: T => Option[Set[T]], loops: scala.collection.mutable.HashSet[Seq[T]])(current: T, path: Seq[T], seen: Set[T]): Unit = {
    if (seen.contains(current)) {
      loops.add(path :+ current)
      ()
    } else {
      graph(current) match {
        case Some(value) =>
          value.foreach {
            referenced =>
              traceCycles(graph, loops)(referenced, path :+ current, seen + current)
          }
        case None =>
      }
    }
  }

}

object Loops extends Loops {}
