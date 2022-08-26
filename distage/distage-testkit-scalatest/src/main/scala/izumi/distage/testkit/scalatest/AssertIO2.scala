package izumi.distage.testkit.scalatest

import izumi.functional.bio.IO2
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] extends AssertIO2Impl[F] {
  def assertIO(arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion]
}

object AssertIO2 extends AssertIO2StaticImpl {}
