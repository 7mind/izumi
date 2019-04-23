package com.github.pshirshov.izumi.distage.testkit.services

import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import distage.DIKey

trait ExternalResourceProvider {
  def process(memoizationContextId: MemoizationContextId, ref: IdentifiedRef): Unit

  def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean

  def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any]

  def save(ctx: MemoizationContextId, key: DIKey, instance: Any): Unit

  def size(ctx: MemoizationContextId): Int

  def size: Int
}

object ExternalResourceProvider {

  object Null extends ExternalResourceProvider {

    override def process(memoizationContextId: MemoizationContextId, ref: IdentifiedRef): Unit = {
      Quirks.discard(memoizationContextId, ref)
    }

    override def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean = false

    override def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any] = None

    override def save(ctx: MemoizationContextId, key: DIKey, instance: Any): Unit = {
      Quirks.discard(ctx, key, instance)
    }


    override def size(ctx: MemoizationContextId): Int = {
      Quirks.discard(ctx)
      0
    }

    override def size: Int = 0
  }

  case class MemoizationKey(ctx: MemoizationContextId, key: DIKey)

  private val cache = new SyncCache[MemoizationKey, Any]()

  def singleton(memoize: IdentifiedRef => Boolean): Singleton = new Singleton(memoize)

  class Singleton(memoize: IdentifiedRef => Boolean) extends ExternalResourceProvider {

    override def process(memoizationContextId: MemoizationContextId, ref: IdentifiedRef): Unit = {
      if (memoize(ref)) {
        save(memoizationContextId, ref.key, ref.value)
      }
    }

    override def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean = {
      cache.hasKey(MemoizationKey(ctx, key))
    }

    override def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any] = {
      cache.get(MemoizationKey(ctx, key))
    }

    override def save(ctx: MemoizationContextId, key: DIKey, instance: Any): Unit = {
      cache.putIfNotExist(MemoizationKey(ctx, key), instance)
    }


    override def size(ctx: MemoizationContextId): Int = {
      cache.enumerate().count(_._1.ctx == ctx)
    }

    override def size: Int = {
      cache.size
    }
  }
}
