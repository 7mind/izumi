package izumi.functional.bio

import izumi.functional.bio.cache.*

import scala.concurrent.duration.FiniteDuration

/** A concurrent, effect-aware cache with TTL support and configurable reference types.
  *
  * Similar to Guava's Cache but designed for bifunctor IO effects.
  *
  * Key properties:
  *   - Concurrent access via lock-free hash map (array of buckets with atomic refs)
  *   - `computeIfAbsent` deduplicates concurrent computations for the same key
  *   - Per-entry and global TTL with lazy or eager eviction strategies
  *   - Configurable reference types (strong, weak, soft) via [[CacheRefType]] typeclass
  *
  * @tparam F bifunctor effect type
  * @tparam K key type
  * @tparam V value type (raw, unwrapped)
  */
trait BIOCache[F[+_, +_], K, V] {

  /** Get a value if present, not expired, and not garbage-collected. */
  def get(key: K): F[Nothing, Option[V]]

  /** Put a value using the global default TTL (if configured). */
  def put(key: K, value: V): F[Nothing, Unit]

  /** Put a value with an explicit TTL. */
  def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Unit]

  /** Get the existing value or compute it. Concurrent calls for the same key are deduplicated:
    * only one fiber computes, others wait for its result. If the computing fiber fails,
    * waiters retry with their own compute function.
    */
  def computeIfAbsent[E](key: K, compute: F[E, V]): F[E, V]

  /** Like [[computeIfAbsent]] but with an explicit TTL for the computed value. */
  def computeIfAbsentWithTTL[E](key: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V]

  /** Remove a single entry. */
  def invalidate(key: K): F[Nothing, Unit]

  /** Remove all entries. */
  def invalidateAll: F[Nothing, Unit]

  /** Current number of non-expired, alive entries (approximate). */
  def size: F[Nothing, Int]

  /** All current keys (approximate snapshot). */
  def keys: F[Nothing, Set[K]]

  /** Shut down background tasks (eviction fiber). No-op for lazy-TTL-only caches. */
  def shutdown: F[Nothing, Unit]
}

object BIOCache {

  /** Create a cache with strong references and lazy TTL eviction.
    *
    * Expired entries are removed on read. No background fiber is started.
    */
  def make[F[+_, +_]: IO2: Primitives2, K, V](config: CacheConfig): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.create[F, K, StrongRef, V](config)
  }

  /** Create a cache with a custom reference type and lazy TTL eviction.
    *
    * Use with [[cache.WeakCacheRef]] or [[cache.SoftCacheRef]] (JVM only) for GC-aware caching.
    */
  def makeWithRef[F[+_, +_]: IO2: Primitives2, K, R[_]: CacheRefType, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.create[F, K, R, V](config)
  }

  /** Create a cache with strong references and eager TTL eviction.
    *
    * Starts a background fiber that periodically scans and evicts expired entries.
    * The caller MUST call [[BIOCache#shutdown]] when the cache is no longer needed,
    * or use [[makeEagerResource]] for automatic lifecycle management.
    *
    * @param config must have `eagerEvictionInterval` set, otherwise behaves like [[make]]
    */
  def makeEager[F[+_, +_]: IO2: Primitives2: Temporal2: Fork2, K, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    makeEagerWithRef[F, K, StrongRef, V](config)
  }

  /** Create a cache with a custom reference type and eager TTL eviction. */
  def makeEagerWithRef[F[+_, +_]: IO2: Primitives2: Temporal2: Fork2, K, R[_]: CacheRefType, V](
    config: CacheConfig
  ): F[Nothing, BIOCache[F, K, V]] = {
    ConcurrentHashMapCache.createWithEviction[F, K, R, V](config)
  }
}
