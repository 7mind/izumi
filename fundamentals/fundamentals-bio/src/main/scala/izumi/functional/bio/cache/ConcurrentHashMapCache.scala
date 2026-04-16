package izumi.functional.bio.cache

import izumi.functional.bio.*

import scala.concurrent.duration.FiniteDuration

/** Lock-free concurrent hash map cache.
  *
  * Uses an array of [[Ref2]] buckets. Each bucket holds an immutable list of entries.
  * Modifications are atomic via [[Ref2#modify]] (CAS with optimistic retry on conflict).
  * Operations on different buckets have zero contention.
  *
  * Concurrent `computeIfAbsent` calls for the same key are deduplicated:
  * the first fiber installs a [[Promise2]] and computes; subsequent fibers wait on it.
  */
private[bio] final class ConcurrentHashMapCache[F[+_, +_], K, R[_], V](
  buckets: Vector[Ref2[F, List[BucketEntry[K, R[V]]]]],
  config: CacheConfig,
  evictionFiberRef: Ref2[F, Option[Fiber2[F, Nothing, Unit]]],
)(implicit
  F: IO2[F],
  P: Primitives2[F],
  R: CacheRefType[R],
) extends BIOCache[F, K, V] {

  private[this] val numBuckets = buckets.size

  private[this] def bucketFor(key: K): Ref2[F, List[BucketEntry[K, R[V]]]] = {
    val h = key.hashCode()
    val spread = h ^ (h >>> 16) // spread high bits (from ConcurrentHashMap)
    buckets(Math.floorMod(spread, numBuckets))
  }

  private[this] def isExpired(expiresAtNano: Long, nowNano: Long): Boolean = {
    expiresAtNano != Long.MaxValue && (nowNano - expiresAtNano) >= 0
  }

  private[this] def isValid(entry: BucketEntry[K, R[V]], nowNano: Long): Boolean = entry match {
    case Ready(_, stored, expiresAtNano) =>
      !isExpired(expiresAtNano, nowNano) && R.get(stored).isDefined
    case _: Computing[_, _] => true
  }

  private[this] def cleanBucket(entries: List[BucketEntry[K, R[V]]], nowNano: Long): List[BucketEntry[K, R[V]]] = {
    entries.filter(isValid(_, nowNano))
  }

  private[this] def computeExpiry(nowNano: Long, ttlOverride: Option[FiniteDuration]): Long = {
    ttlOverride.orElse(config.defaultTTL) match {
      case Some(ttl) => nowNano + ttl.toNanos
      case None => Long.MaxValue
    }
  }

  override def get(key: K): F[Nothing, Option[V]] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      bucketFor(key).modify { entries =>
        val cleaned = cleanBucket(entries, nowNano)
        cleaned.find(_.key == key) match {
          case Some(Ready(_, stored, _)) =>
            R.get(stored) match {
              case s @ Some(_) => (s, cleaned)
              case None => (None, cleaned.filterNot(_.key == key))
            }
          case _ => (None, cleaned)
        }
      }
    }
  }

  override def put(key: K, value: V): F[Nothing, Unit] = putImpl(key, value, None)

  override def putWithTTL(key: K, value: V, ttl: FiniteDuration): F[Nothing, Unit] = putImpl(key, value, Some(ttl))

  private[this] def putImpl(key: K, value: V, ttl: Option[FiniteDuration]): F[Nothing, Unit] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      val stored = R.wrap(value)
      val expiry = computeExpiry(nowNano, ttl)
      bucketFor(key).update_ { entries =>
        Ready[K, R[V]](key, stored, expiry) :: cleanBucket(entries, nowNano).filterNot(_.key == key)
      }
    }
  }

  override def computeIfAbsent[E](key: K, compute: F[E, V]): F[E, V] =
    computeImpl(key, None, compute)

  override def computeIfAbsentWithTTL[E](key: K, ttl: FiniteDuration, compute: F[E, V]): F[E, V] =
    computeImpl(key, Some(ttl), compute)

  // Action tags for computeImpl dispatch (avoids GADT variance issues with sealed trait)
  private[this] val ActionHit = 0
  private[this] val ActionWait = 1
  private[this] val ActionCompute = 2

  private[this] def computeImpl[E](key: K, ttl: Option[FiniteDuration], compute: F[E, V]): F[E, V] = {
    val bucketRef = bucketFor(key)
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.flatMap(P.mkPromise[Nothing, Option[V]]) { promise =>
        // modify returns (tag, payload) where payload is either the hit value or a promise (as AnyRef)
        F.flatMap(bucketRef.modify { entries =>
          val cleaned = cleanBucket(entries, nowNano)
          cleaned.find(_.key == key) match {
            case Some(Ready(_, stored, _)) =>
              R.get(stored) match {
                case Some(v) =>
                  ((ActionHit, v.asInstanceOf[AnyRef]), cleaned)
                case None =>
                  val newEntries = Computing[K, R[V]](key, promise) :: cleaned.filterNot(_.key == key)
                  ((ActionCompute, null), newEntries)
              }
            case Some(Computing(_, existingPromise)) =>
              ((ActionWait, existingPromise), cleaned)
            case None =>
              val newEntries = Computing[K, R[V]](key, promise) :: cleaned
              ((ActionCompute, null), newEntries)
          }
        }) { case (tag, payload) =>
          if (tag == ActionHit) {
            F.pure(payload.asInstanceOf[V])
          } else if (tag == ActionWait) {
            awaitAndRetry(key, ttl, compute, payload.asInstanceOf[Promise2[F, Nothing, Option[V]]])
          } else {
            doCompute(key, bucketRef, promise, ttl, compute)
          }
        }
      }
    }
  }

  private[this] def awaitAndRetry[E](
    key: K,
    ttl: Option[FiniteDuration],
    compute: F[E, V],
    existingPromise: Promise2[F, Nothing, Option[V]],
  ): F[E, V] = {
    F.flatMap(existingPromise.await) {
      case Some(v) => F.pure(v)
      case None =>
        // Computation failed - retry with our own compute function
        computeImpl(key, ttl, compute)
    }
  }

  private[this] def doCompute[E](
    key: K,
    bucketRef: Ref2[F, List[BucketEntry[K, R[V]]]],
    myPromise: Promise2[F, Nothing, Option[V]],
    ttl: Option[FiniteDuration],
    compute: F[E, V],
  ): F[E, V] = {
    F.guaranteeOnFailure(
      F.flatMap(F.sandboxExit(compute)) { exit =>
        exit match {
          case Exit.Success(v) =>
            F.flatMap(F.sync(System.nanoTime())) { nowNano =>
              val stored = R.wrap(v)
              val expiry = computeExpiry(nowNano, ttl)
              F.flatMap(bucketRef.update_ { entries =>
                Ready[K, R[V]](key, stored, expiry) :: entries.filterNot(_.key == key)
              }) { _ =>
                F.flatMap(F.void(myPromise.succeed(Some(v)))) { _ =>
                  F.fromSandboxExit(exit)
                }
              }
            }
          case _: Exit.FailureUninterrupted[?] =>
            // Computation failed - remove Computing entry, signal waiters to retry
            F.flatMap(bucketRef.update_(_.filterNot(_.key == key))) { _ =>
              F.flatMap(F.void(myPromise.succeed(None))) { _ =>
                F.fromSandboxExit(exit)
              }
            }
        }
      },
      // On interruption or defect in our code: clean up
      { (_: Exit.Failure[E]) =>
        F.flatMap(bucketRef.update_(_.filterNot(_.key == key))) { _ =>
          F.void(myPromise.succeed(None))
        }
      },
    )
  }

  override def invalidate(key: K): F[Nothing, Unit] = {
    bucketFor(key).update_(_.filterNot(_.key == key))
  }

  override def invalidateAll: F[Nothing, Unit] = {
    F.traverse_(buckets)(_.set(Nil))
  }

  override def size: F[Nothing, Int] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(buckets.toList) { ref =>
        F.map(ref.get) { entries =>
          entries.count {
            case r: Ready[_, _] => !isExpired(r.expiresAtNano, nowNano) && R.get(r.stored.asInstanceOf[R[V]]).isDefined
            case _ => false
          }
        }
      })(_.sum)
    }
  }

  override def keys: F[Nothing, Set[K]] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.map(F.traverse(buckets.toList) { ref =>
        F.map(ref.get) { entries =>
          entries.collect {
            case r @ Ready(k, _, expiresAtNano) if !isExpired(expiresAtNano, nowNano) && R.get(r.stored.asInstanceOf[R[V]]).isDefined => k
          }
        }
      })(_.flatten.toSet)
    }
  }

  override def shutdown: F[Nothing, Unit] = {
    F.flatMap(evictionFiberRef.get) {
      case Some(fiber) => fiber.interrupt
      case None => F.unit
    }
  }

  private[cache] def evictExpired: F[Nothing, Unit] = {
    F.flatMap(F.sync(System.nanoTime())) { nowNano =>
      F.traverse_(buckets.toList) { ref =>
        ref.update_ { entries =>
          cleanBucket(entries, nowNano)
        }
      }
    }
  }
}

// Bucket entry types
private[cache] sealed trait BucketEntry[K, +S] {
  def key: K
}
private[cache] final case class Ready[K, S](key: K, stored: S, expiresAtNano: Long) extends BucketEntry[K, S]
private[cache] final case class Computing[K, S](key: K, promise: AnyRef) extends BucketEntry[K, Nothing]

private[bio] object ConcurrentHashMapCache {

  def create[F[+_, +_], K, R[_], V](
    config: CacheConfig
  )(implicit F: IO2[F],
    P: Primitives2[F],
    R: CacheRefType[R],
  ): F[Nothing, BIOCache[F, K, V]] = {
    val n = Math.max(1, config.initialCapacity)
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(List.empty[BucketEntry[K, R[V]]]))) { bucketList =>
      F.map(P.mkRef(Option.empty[Fiber2[F, Nothing, Unit]])) { fiberRef =>
        new ConcurrentHashMapCache[F, K, R, V](bucketList.toVector, config, fiberRef): BIOCache[F, K, V]
      }
    }
  }

  def createWithEviction[F[+_, +_], K, R[_], V](
    config: CacheConfig
  )(implicit F: IO2[F],
    P: Primitives2[F],
    R: CacheRefType[R],
    T: Temporal2[F],
    FK: Fork2[F],
  ): F[Nothing, BIOCache[F, K, V]] = {
    val n = Math.max(1, config.initialCapacity)
    F.flatMap(F.traverse((0 until n).toList)(_ => P.mkRef(List.empty[BucketEntry[K, R[V]]]))) { bucketList =>
      val buckets = bucketList.toVector
      F.flatMap(P.mkRef(Option.empty[Fiber2[F, Nothing, Unit]])) { fiberRef =>
        val cache = new ConcurrentHashMapCache[F, K, R, V](buckets, config, fiberRef)
        config.eagerEvictionInterval match {
          case Some(interval) =>
            val evictionLoop: F[Nothing, Unit] =
              F.tailRecM[Nothing, Unit, Nothing](()) { _ =>
                F.map(F.*>(T.sleep(interval), cache.evictExpired))(_ => Left(()))
              }
            F.flatMap(FK.fork(evictionLoop)) { fiber =>
              F.map(fiberRef.set(Some(fiber)))(_ => cache: BIOCache[F, K, V])
            }
          case None =>
            F.pure(cache: BIOCache[F, K, V])
        }
      }
    }
  }
}
