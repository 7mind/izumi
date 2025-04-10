package izumi.distage.testkit.scalatest

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import zio.IO

trait ShorthandAssertionsZIO {
  def assertIO[T](effect: IO[Nothing, T])(predicate: T => Boolean)(implicit prettifier: Prettifier, pos: Position, zioTrace: zio.Trace): IO[Nothing, Assertion] = {
    effect.flatMap(value => AssertZIO.assertIO(predicate(value)))
  }

  def assertIO[A, B](
    effectA: IO[Nothing, A],
    effectB: IO[Nothing, B],
  )(predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): IO[Nothing, Assertion] = {
    for {
      valueA <- effectA
      valueB <- effectB
      assertion <- AssertZIO.assertIO(predicate(valueA, valueB))
    } yield assertion
  }
}
