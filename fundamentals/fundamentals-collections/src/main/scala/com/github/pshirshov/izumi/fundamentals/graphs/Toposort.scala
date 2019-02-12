package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec

trait Toposort {

  private def isInvolvedIntoCycle[T](toPreds: Map[T, scala.collection.immutable.Set[T]])(key: T): Boolean = {
    test(toPreds, Set.empty, key, key)
  }

  private def test[T](toPreds: Map[T, scala.collection.immutable.Set[T]], stack: Set[T], toTest: T, needle: T): Boolean = {
    val deps = toPreds.getOrElse(toTest, Set.empty)

    if (deps.contains(needle)) {
      true
    } else {
      deps.exists {
        d =>
          if (stack.contains(d)) {
           false
          } else {
            test(toPreds, stack + d, d, needle)
          }
      }
    }
  }

  @tailrec
  final def cycleBreaking[T](toPreds: Map[T, scala.collection.immutable.Set[T]], done: Seq[T], break: Set[T] => T): Seq[T] = {
    val (noPreds, hasPreds) = toPreds.partition {
      _._2.isEmpty
    }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        done
      } else { // circular dependency
        val loopMembers = hasPreds.filterKeys(isInvolvedIntoCycle(hasPreds))
        val breakLoopAt = break(loopMembers.keySet)
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
