package izumi.fundamentals.orphans

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

// cats-effect

/**
  * This instance uses 'no more orphans' trick to provide an Optional instance
  * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
  *
  * Optional instance via https://blog.7mind.io/no-more-orphans.html
  */
final abstract class `cats.effect.IO`[K[_]]
object `cats.effect.IO` {
  @inline implicit final def get: `cats.effect.IO`[cats.effect.IO] = null
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
