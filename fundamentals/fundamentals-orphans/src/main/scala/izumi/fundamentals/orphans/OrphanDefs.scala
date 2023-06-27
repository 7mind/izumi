package izumi.fundamentals.orphans

import scala.annotation.unused

/**
  * `No More Orphans` type providers. See detail https://blog.7mind.io/no-more-orphans.html
  *
  * These instances uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */

// cats-kernel

final abstract class `cats.kernel.PartialOrder with cats.kernel.Hash`[K[_]]
object `cats.kernel.PartialOrder with cats.kernel.Hash` {
  type PartialOrderHashType[T] = cats.kernel.PartialOrder[T] & cats.kernel.Hash[T]
  @inline implicit final def get[K[_]](
    implicit @unused guard: `cats.kernel.BoundedSemilattice`[K]
  ): `cats.kernel.PartialOrder with cats.kernel.Hash`[PartialOrderHashType] = null
}

// cats-core

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.Functor`[R[_[_]]]
object `cats.Functor` {
  @inline implicit final def get: `cats.Functor`[cats.Functor] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.Applicative`[R[_[_]]]
object `cats.Applicative` {
  @inline implicit final def get: `cats.Applicative`[cats.Applicative] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.Monad`[M[_[_]]]
object `cats.Monad` {
  @inline implicit final def get: `cats.Monad`[cats.Monad] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.Parallel`[K[_[_]]]
object `cats.Parallel` {
  @inline implicit final def get: `cats.Parallel`[cats.Parallel] = null
}

// cats-effect

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.kernel.Sync`[R[_[_]]]
object `cats.effect.kernel.Sync` {
  @inline implicit final def get: `cats.effect.kernel.Sync`[cats.effect.kernel.Sync] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.kernel.Async`[R[_[_]]]
object `cats.effect.kernel.Async` {
  @inline implicit final def get: `cats.effect.kernel.Async`[cats.effect.kernel.Async] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.kernel.MonadCancel`[R[_[_], E]]
object `cats.effect.kernel.MonadCancel` {
  @inline implicit final def get: `cats.effect.kernel.MonadCancel`[cats.effect.kernel.MonadCancel] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.kernel.GenTemporal`[R[_[_], E]]
object `cats.effect.kernel.GenTemporal` {
  @inline implicit final def get: `cats.effect.kernel.GenTemporal`[cats.effect.kernel.GenTemporal] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.std.Dispatcher`[R[_[_]]]
object `cats.effect.std.Dispatcher` {
  @inline implicit final def get: `cats.effect.std.Dispatcher`[cats.effect.std.Dispatcher] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.unsafe.IORuntime`[R]
object `cats.effect.unsafe.IORuntime` {
  @inline implicit final def get: `cats.effect.unsafe.IORuntime`[cats.effect.unsafe.IORuntime] = null
}

// zio

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `zio.ZIO`[K[_, _, _]]
object `zio.ZIO` {
  @inline implicit final def get: `zio.ZIO`[zio.ZIO] = null
}

// zio-interop-cats

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `zio.interop.ZManagedSyntax`[K[_, _, _]]
object `zio.interop.ZManagedSyntax` {
  @inline implicit final def get: `zio.interop.ZManagedSyntax`[zio.interop.ZManagedSyntax] = null
}

//// monix-bio
//
///**
//  * This instance uses 'no more orphans' trick to provide an Optional instance
//  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
//  *
//  * Optional instance via https://blog.7mind.io/no-more-orphans.html
//  */
//final abstract class `monix.bio.IO`[K[_, _]]
//object `monix.bio.IO` {
//  @inline implicit final def get: `monix.bio.IO`[monix.bio.IO] = null
//}
