package com.github.pshirshov.izumi.distage.testkit.services

import scala.collection.mutable

class SyncCache[K, V] {
  private val cache = new mutable.HashMap[K, V]()

  def getOrCompute(k: K, default: => V): V = cache.synchronized {
    cache.getOrElseUpdate(k, default)
  }

  def clear(): Unit = {
    cache.clear()
  }
}
