package com.github.pshirshov.izumi.fundamentals.collections

import scala.annotation.tailrec

trait Toposort {
  @tailrec
  final def cycleBreaking[T](toPreds: Map[T, Set[T]], done: Seq[T]): Seq[T] = {
    val (noPreds, hasPreds) = toPreds.partition {
      _._2.isEmpty
    }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency, trying to break it by removing head
        val found = Seq(hasPreds.head._1)
        cycleBreaking(hasPreds.tail.mapValues {
          _ -- found
        }, done ++ found)
      }
    } else {
      val found = noPreds.keys
      cycleBreaking(hasPreds.mapValues {
        _ -- found
      }, done ++ found)
    }
  }

}

trait Graphs {
  def toposort: Toposort
}

object Graphs extends Graphs {
  private object Toposort extends Toposort
  override def toposort: Toposort = Toposort
}
