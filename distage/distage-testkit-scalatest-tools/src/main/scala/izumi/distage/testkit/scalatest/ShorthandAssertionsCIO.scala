package izumi.distage.testkit.scalatest

import cats.effect.IO
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

trait ShorthandAssertionsCIO {
  def assertIO[T](effect: IO[T])(predicate: T => Boolean)(implicit prettifier: Prettifier, pos: source.Position): IO[Assertion] = {
    effect.flatMap(value => AssertCIO.assertIO(predicate(value)))
  }

  def assertIO[A, B](effectA: IO[A], effectB: IO[B])(predicate: (A, B) => Boolean)(implicit prettifier: Prettifier, pos: source.Position): IO[Assertion] = {
    for {
      valueA <- effectA
      valueB <- effectB
      assertion <- AssertCIO.assertIO(predicate(valueA, valueB))
    } yield assertion
  }
}
