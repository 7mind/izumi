package com.github.pshirshov.izumi.fundamentals.graphs

import scala.annotation.tailrec

class Toposort {
  import Toposort._

  @tailrec
  final def cycleBreaking[T](toPreds: Map[T, scala.collection.immutable.Set[T]], done: Seq[T], break: Set[T] => T): Either[InconsistentInput[T], Seq[T]] = {
    val (noPreds, hasPreds) = toPreds.partition {
      _._2.isEmpty
    }

    if (noPreds.isEmpty) {
      if (hasPreds.isEmpty) {
        Right(done)
      } else { // circular dependency
        val loopMembers = hasPreds.filterKeys(isInvolvedIntoCycle(hasPreds))
        if (loopMembers.nonEmpty) {
          val breakLoopAt = break(loopMembers.keySet)
          val found = Set(breakLoopAt)
          val next = hasPreds.filterKeys(k => k != breakLoopAt).mapValues(_ -- found).toMap

          cycleBreaking(next, done ++ found, break) // 2.13 compat
        } else {
          Left(InconsistentInput(hasPreds))
        }
      }
    } else {
      val found = noPreds.keySet
      val next = hasPreds.mapValues(_ -- found).toMap
      cycleBreaking(next, done ++ found, break) // 2.13 compat
    }
  }

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

}

object Toposort {
  final case class InconsistentInput[T](issues: Map[T, scala.collection.immutable.Set[T]])
}
