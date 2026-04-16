package izumi.functional.bio.cache

import scala.concurrent.duration.FiniteDuration

final case class CacheConfig(
  initialCapacity: Int = 16,
  defaultTTL: Option[FiniteDuration] = None,
  /** If set, a background fiber will periodically scan and evict expired entries.
    * Requires [[izumi.functional.bio.Temporal2]] and [[izumi.functional.bio.Fork2]] at construction.
    * If None, TTL is enforced lazily on reads.
    */
  eagerEvictionInterval: Option[FiniteDuration] = None,
)
