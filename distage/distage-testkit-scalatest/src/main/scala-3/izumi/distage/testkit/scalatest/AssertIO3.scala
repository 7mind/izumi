package izumi.distage.testkit.scalatest

import izumi.functional.bio.IO3
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions}

/** scalatest assertion macro for any [[izumi.functional.bio.IO3]] */
trait AssertIO3[F[-_, +_, +_]] {
  inline final def assertIO(inline arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] = {
    IO3.sync(Assertions.assert(arg))
  }
}

object AssertIO3 {
  inline final def assertIO[F[-_, +_, +_]](inline arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] = {
    IO3.sync(Assertions.assert(arg))
  }
}
