package izumi.fundamentals.orphans

import scala.annotation.unused

// FIXME Split instances for F[_]-kind because of a Scala 3 bug https://github.com/lampepfl/dotty/issues/16183

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
  @inline implicit final def get: `cats.kernel.BoundedSemilattice`[[A] =>> cats.kernel.BoundedSemilattice[A]] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.kernel.Monoid`[K[_]]
object `cats.kernel.Monoid` {
  @inline implicit final def get: `cats.kernel.Monoid`[[A] =>> cats.kernel.Monoid[A]] = null
}

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.kernel.Semigroup`[S[_]]
object `cats.kernel.Semigroup` {
  @inline implicit final def get: `cats.kernel.Semigroup`[[A] =>> cats.kernel.Semigroup[A]] = null
}

// cats-effect

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.IO`[K[_]]
object `cats.effect.IO` {
  @inline implicit final def get: `cats.effect.IO`[[A] =>> cats.effect.IO[A]] = null
}

//// monix
//
///**
//  * This instance uses 'no more orphans' trick to provide an Optional instance
//  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
//  *
//  * Optional instance via https://blog.7mind.io/no-more-orphans.html
//  */
//final abstract class `monix.eval.Task`[K[_]]
//object `monix.eval.Task` {
//  @inline implicit final def get: `monix.eval.Task`[monix.eval.Task] = null
//}
