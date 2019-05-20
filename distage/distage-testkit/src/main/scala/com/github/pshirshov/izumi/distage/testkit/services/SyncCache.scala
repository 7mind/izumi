package com.github.pshirshov.izumi.distage.testkit.services

import scala.collection.mutable
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

class SyncCache[K, V] {

  def enumerate(): Seq[(K, V)] = sync {
    cache.toSeq
  }

  private val cache = new mutable.HashMap[K, V]()

  def getOrCompute(k: K, default: => V): V = sync {
    cache.getOrElseUpdate(k, default)
  }

  def put(k: K, v: V): Unit = sync {
    cache.put(k, v).discard()
  }

  def get(k: K): Option[V] = sync {
    cache.get(k)
  }

  def clear(): Unit = sync {
    cache.clear()
  }

  def hasKey(k: K): Boolean = sync {
    cache.contains(k)
  }

  def putIfNotExist(k: K, v: => V): Unit = sync {
    if (!cache.contains(k)) {
      cache.put(k, v).discard()
    }
  }

  def size: Int = sync {
    cache.size
  }

  private def sync[T](f: => T): T = {
    cache.synchronized {
      f
    }
  }
}
