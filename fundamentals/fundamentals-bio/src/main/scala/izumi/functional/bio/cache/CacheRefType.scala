package izumi.functional.bio.cache

/** Typeclass for cache value reference strategies.
  *
  * Determines how values are stored internally (strong, weak, soft).
  * Encode the reference type in the cache's type signature and use
  * this typeclass to provide behaviors:
  * {{{
  *   BIOCache.makeWithRef[F, Key, WeakCacheRef, Value](config)
  * }}}
  */
trait CacheRefType[R[_]] {
  def wrap[V](value: V): R[V]
  /** Returns None if the reference was garbage-collected */
  def get[V](ref: R[V]): Option[V]
}

object CacheRefType {
  @inline def apply[R[_]](implicit ev: CacheRefType[R]): CacheRefType[R] = ev
}

/** Strong reference - values are never garbage-collected. */
final case class StrongRef[+V](value: V)

object StrongRef {
  implicit val cacheRefType: CacheRefType[StrongRef] = new CacheRefType[StrongRef] {
    override def wrap[V](value: V): StrongRef[V] = StrongRef(value)
    override def get[V](ref: StrongRef[V]): Option[V] = Some(ref.value)
  }
}
