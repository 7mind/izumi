package izumi.distage.testkit.scalatest

import izumi.functional.bio.{IO2, Monad2}
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

trait ShorthandAssertionsIO2[F[+_, +_]] {
  def assertIO[T](effect: F[Nothing, T])(predicate: T => Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = {
    effect.flatMap(value => AssertIO2.assertIO(predicate(value)))
  }

  def assertIO[A, B](
    effectA: F[Nothing, A],
    effectB: F[Nothing, B],
  )(predicate: (A, B) => Boolean
  )(implicit IO2: IO2[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Nothing, Assertion] = {
    for {
      valueA <- effectA
      valueB <- effectB
      assertion <- AssertIO2.assertIO(predicate(valueA, valueB))
    } yield assertion
  }
}
