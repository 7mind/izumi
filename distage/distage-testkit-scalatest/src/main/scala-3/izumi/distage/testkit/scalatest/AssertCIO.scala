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
}

object AssertCIO extends AssertCIO
