package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec

trait Toposort {
  @tailrec
  final def cycleBreaking[T](toPreds: Map[T, scala.collection.immutable.Set[T]], done: Seq[T]): Seq[T] = {
    val (noPreds, hasPreds) = toPreds.partition {
      _._2.isEmpty
    }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency, trying to break it by removing head
        val found = Set(hasPreds.head._1)
        cycleBreaking(hasPreds.tail.mapValues {
          _ -- found
        }.toMap, done ++ found) // 2.13 compat
      }
    } else {
      val found = noPreds.keySet
      cycleBreaking(hasPreds.mapValues {
        _ -- found
      }.toMap, done ++ found) // 2.13 compat
    }
  }

}
