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
}

object AssertZIO extends AssertZIO
