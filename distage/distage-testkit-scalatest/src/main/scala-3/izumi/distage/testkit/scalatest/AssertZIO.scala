package izumi.distage.testkit.scalatest

import izumi.functional.bio.{IO2, IO3}
import org.scalactic.source.Position
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions, AssertionsMacro}
import zio.IO

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO {
  inline final def assertIO(inline arg: Boolean)(implicit prettifier: Prettifier, pos: Position): IO[Nothing, Assertion] = {
    IO.effectTotal(Assertions.assert(arg))
  }
}

object AssertZIO extends AssertZIO
