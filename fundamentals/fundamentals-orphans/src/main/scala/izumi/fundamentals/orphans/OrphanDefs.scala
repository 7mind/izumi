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

final abstract class `cats.kernel.BoundedSemilattice`[K[_]]
object `cats.kernel.BoundedSemilattice` {
  @inline implicit final def get: `cats.kernel.BoundedSemilattice`[cats.kernel.BoundedSemilattice] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.kernel.Monoid`[K[_]]
object `cats.kernel.Monoid` {
  @inline implicit final def get: `cats.kernel.Monoid`[cats.kernel.Monoid] = null
}

// cats-core

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-core as a dependency without REQUIRING a cats-core dependency.
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

final abstract class `cats.Monad`[M[_[_]]]
object `cats.Monad` {
  @inline implicit final def get: `cats.Monad`[cats.Monad] = null
}

final abstract class `cats.Semigroup`[S[_]]
object `cats.Semigroup` {
  @inline implicit final def get: `cats.Semigroup`[cats.Semigroup] = null
}

final abstract class `cats.Parallel`[K[_[_]]]
object `cats.Parallel` {
  @inline implicit final def get: `cats.Parallel`[cats.Parallel] = null
}

final abstract class `cats.kernel.PartialOrder with cats.kernel.Hash`[K[_]]
object `cats.kernel.PartialOrder with cats.kernel.Hash` {
  type PartialOrderHashType[T] = cats.kernel.PartialOrder[T] with cats.kernel.Hash[T]
  @inline implicit final def get[K[_]](
    implicit @unused guard: `cats.kernel.BoundedSemilattice`[K]
  ): `cats.kernel.PartialOrder with cats.kernel.Hash`[PartialOrderHashType] = null
}

// cats-effect

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.Sync`[R[_[_]]]
object `cats.effect.Sync` {
  @inline implicit final def get: `cats.effect.Sync`[cats.effect.Sync] = null
}

final abstract class `cats.effect.Bracket`[R[_[_], E]]
object `cats.effect.Bracket` {
  @inline implicit final def get: `cats.effect.Bracket`[cats.effect.MonadCancel] = null
}

final abstract class `cats.effect.Concurrent`[K[_[_]]]
object `cats.effect.Concurrent` {
  @inline implicit final def get: `cats.effect.Concurrent`[cats.effect.Concurrent] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.Effect`[R[_[_]]]
object `cats.effect.Effect` {
  @inline implicit final def get: `cats.effect.Effect`[cats.effect.Effect] = null
}

final abstract class `cats.effect.ConcurrentEffect`[K[_[_]]]
object `cats.effect.ConcurrentEffect` {
  @inline implicit final def get: `cats.effect.ConcurrentEffect`[cats.effect.ConcurrentEffect] = null
}

final abstract class `cats.effect.Timer`[K[_[_]]]
object `cats.effect.Timer` {
  @inline implicit final def get: `cats.effect.Timer`[cats.effect.Temporal] = null
}

final abstract class `cats.effect.ContextShift`[K[_[_]]]
object `cats.effect.ContextShift` {
  @inline implicit final def get: `cats.effect.ContextShift`[cats.effect.ContextShift] = null
}

final abstract class `cats.effect.IO`[K[_]]
object `cats.effect.IO` {
  @inline implicit final def get: `cats.effect.IO`[cats.effect.IO] = null
}

// zio

final abstract class `zio.ZIO`[K[_, _, _]]
object `zio.ZIO` {
  @inline implicit final def get: `zio.ZIO`[zio.ZIO] = null
}

// zio-interop-cats

final abstract class `zio.interop.ZManagedSyntax`[K[_, _, _]]
object `zio.interop.ZManagedSyntax` {
  @inline implicit final def get: `zio.interop.ZManagedSyntax`[zio.interop.ZManagedSyntax] = null
}

// monix-bio

final abstract class `monix.bio.IO`[K[_, _]]
object `monix.bio.IO` {
  @inline implicit final def get: `monix.bio.IO`[monix.bio.IO] = null
}

// monix

final abstract class `monix.eval.Task`[K[_]]
object `monix.eval.Task` {
  @inline implicit final def get: `monix.eval.Task`[monix.eval.Task] = null
}
