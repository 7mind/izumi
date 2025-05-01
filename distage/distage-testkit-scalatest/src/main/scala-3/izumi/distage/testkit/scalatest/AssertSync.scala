package izumi.distage.testkit.scalatest

import cats.FlatMap
import cats.effect.kernel.Sync
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for any [[cats.effect.kernel.Sync]] */
trait AssertSync[F[_]] {
  inline final def assertIO(inline arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.delay(Assertions.assert(arg))
  }

  inline final def assertIO[T](
    inline effect: F[T]
  )(inline predicate: T => Boolean
  )(implicit Sync: Sync[F],
    FlatMap: FlatMap[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Assertion] = {
    FlatMap.flatMap(effect)(result => assertIO(predicate(result)))
  }

  inline final def assertIO[A, B](
    inline effectA: F[A],
    inline effectB: F[B],
  )(inline predicate: (A, B) => Boolean
  )(implicit Sync: Sync[F],
    FlatMap: FlatMap[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Assertion] = {
    FlatMap.flatMap(effectA)(resultA => FlatMap.flatMap(effectB)(resultB => assertIO(predicate(resultA, resultB))))
  }
}

object AssertSync {
  inline final def assertIO[F[_]](inline arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = {
    Sync.delay(Assertions.assert(arg))
  }
}
