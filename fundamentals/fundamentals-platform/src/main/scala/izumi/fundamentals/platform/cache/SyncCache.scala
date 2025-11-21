package izumi.fundamentals.platform.cache

import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

class SyncCache[K, V] {
  private val cache = new mutable.HashMap[K, V]()

  def enumerate(): Seq[(K, V)] = synchronize {
    cache.toSeq
  }

  def getOrCompute(k: K, default: => V): V = synchronize {
    cache.getOrElseUpdate(k, default)
  }

  def put(k: K, v: V): Unit = synchronize {
    cache.put(k, v).discard()
  }

  def get(k: K): Option[V] = synchronize {
    cache.get(k)
  }

  def clear(): Unit = synchronize {
    cache.clear()
  }

  def hasKey(k: K): Boolean = synchronize {
    cache.contains(k)
  }

  def putIfNotExist(k: K, v: => V): Unit = synchronize {
    if (!cache.contains(k)) {
      cache.put(k, v).discard()
    }
  }

  def size: Int = synchronize {
    cache.size
  }

  private def synchronize[T](f: => T): T = {
    cache.synchronized {
      f
    }
  }

}
