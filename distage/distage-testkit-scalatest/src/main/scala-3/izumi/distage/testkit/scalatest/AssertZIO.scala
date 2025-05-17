package izumi.distage.testkit.scalatest

import org.scalactic.source.Position
import org.scalactic.Prettifier
import org.scalatest.{Assertion, Assertions}
import zio.ZIO

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO {
  inline final def assertIO(inline arg: Boolean)(implicit prettifier: Prettifier, pos: Position, zioTrace: zio.Trace): ZIO[Any, Nothing, Assertion] = {
    ZIO.succeed(Assertions.assert(arg))(zioTrace)
  }

  inline final def assertIO[R, E, T](
    inline effect: ZIO[R, E, T]
  )(inline predicate: T => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): ZIO[R, E, Assertion] = {
    effect.flatMap(result => assertIO(predicate(result)))
  }

  inline final def assertIO[R, E, A, B](
    inline effectA: ZIO[R, E, A],
    inline effectB: ZIO[R, E, B],
  )(inline predicate: (A, B) => Boolean
  )(implicit prettifier: Prettifier,
    pos: Position,
    zioTrace: zio.Trace,
  ): ZIO[R, E, Assertion] = {
    effectA.flatMap(resultA => effectB.flatMap(resultB => assertIO(predicate(resultA, resultB))))
  }
}

object AssertZIO extends AssertZIO
