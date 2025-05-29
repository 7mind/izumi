package izumi.distage.testkit.scalatest

import cats.effect.IO
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for [[cats.effect.IO]] */
trait AssertCIO {
  inline final def assertIO(inline arg: Boolean)(implicit prettifier: Prettifier, pos: Position): IO[Assertion] = {
    IO.delay(Assertions.assert(arg))
  }

  inline final def assertIO[T](
    inline effect: IO[T]
  )(inline predicate: T => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
  ): IO[Assertion] = {
    effect.flatMap(result => assertIO(predicate(result)))
  }

  inline final def assertIO[A, B](
    inline effectA: IO[A],
    inline effectB: IO[B],
  )(inline predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
  ): IO[Assertion] = {
    effectA.flatMap(resultA => effectB.flatMap(resultB => assertIO(predicate(resultA, resultB))))
  }
}

object AssertCIO extends AssertCIO
