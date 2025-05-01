package izumi.distage.testkit.scalatest

import izumi.functional.bio.IO2
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] {
  inline final def assertIO(inline arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = {
    IO2.sync(Assertions.assert(arg))
  }

  inline final def assertIO[T](
    inline effect: F[Nothing, T]
  )(inline predicate: T => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Nothing, Assertion] = {
    IO2.flatMap(effect)(result => assertIO(predicate(result)))
  }

  inline final def assertIO[A, B](
    inline effectA: F[Nothing, A],
    inline effectB: F[Nothing, B],
  )(inline predicate: (A, B) => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Nothing, Assertion] = {
    IO2.flatMap(effectA)(resultA => IO2.flatMap(effectB)(resultB => assertIO(predicate(resultA, resultB))))
  }
}

object AssertIO2 {
  inline final def assertIO[F[+_, +_]](inline arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = {
    IO2.sync(Assertions.assert(arg))
  }
}
