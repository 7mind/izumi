package izumi.functional.bio.cache

import java.lang.ref.{SoftReference, WeakReference}

/** Weak reference wrapper for cache values (JVM only).
  *
  * Values wrapped in [[WeakCacheRef]] can be garbage-collected when there
  * are no strong references to them outside the cache.
  * Cache reads will return `None` for collected entries.
  */
final class WeakCacheRef[V] private[cache] (private val ref: WeakReference[Any]) extends AnyVal {
  def get: Option[V] = Option(ref.get().asInstanceOf[V])
}

object WeakCacheRef {
  implicit val cacheRefType: CacheRefType[WeakCacheRef] = new CacheRefType[WeakCacheRef] {
    override def wrap[V](value: V): WeakCacheRef[V] = {
      new WeakCacheRef[V](new WeakReference[Any](value))
    }
    override def get[V](ref: WeakCacheRef[V]): Option[V] = ref.get
  }
}

/** Soft reference wrapper for cache values (JVM only).
  *
  * Values wrapped in [[SoftCacheRef]] can be garbage-collected under
  * memory pressure. The JVM guarantees soft references are cleared
  * before throwing `OutOfMemoryError`.
  */
final class SoftCacheRef[V] private[cache] (private val ref: SoftReference[Any]) extends AnyVal {
  def get: Option[V] = Option(ref.get().asInstanceOf[V])
}

object SoftCacheRef {
  implicit val cacheRefType: CacheRefType[SoftCacheRef] = new CacheRefType[SoftCacheRef] {
    override def wrap[V](value: V): SoftCacheRef[V] = {
      new SoftCacheRef[V](new SoftReference[Any](value))
    }
    override def get[V](ref: SoftCacheRef[V]): Option[V] = ref.get
  }
}
