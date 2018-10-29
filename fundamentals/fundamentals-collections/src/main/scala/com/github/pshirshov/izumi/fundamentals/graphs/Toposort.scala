package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec

trait Toposort {
  @tailrec
  final def cycleBreaking[T](toPreds: Map[T, scala.collection.immutable.Set[T]], done: Seq[T], break: Map[T, scala.collection.immutable.Set[T]] => T): Seq[T] = {
    val (noPreds, hasPreds) = toPreds.partition {
      _._2.isEmpty
    }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency, trying to break it by removing head
        val breakLoopAt = break(hasPreds)
        val found = Set(breakLoopAt)
        val next = hasPreds.filterKeys(k => k != breakLoopAt).mapValues(_ -- found).toMap
        cycleBreaking(next, done ++ found, break) // 2.13 compat
      }
    } else {
      val found = noPreds.keySet
      val next = hasPreds.mapValues(_ -- found).toMap
      cycleBreaking(next, done ++ found, break) // 2.13 compat
    }
  }

}
