package izumi.distage.testkit.scalatest

import izumi.functional.bio.IO2
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] {
  inline final def assertIO(inline arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = {
    IO2.sync(Assertions.assert(arg))
  }
}

object AssertIO2 {
  inline final def assertIO[F[+_, +_]](inline arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = {
    IO2.sync(Assertions.assert(arg))
  }
}
