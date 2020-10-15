package izumi.distage.testkit.scalatest

import cats.effect.IO
import izumi.distage.testkit.scalatest.AssertCIO.AssertCIOMacro
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[cats.effect.IO]] */
trait AssertCIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): IO[Assertion] = macro AssertCIOMacro.impl
}

object AssertCIO extends AssertCIO {

  object AssertCIOMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[IO[Assertion]] = {
      import c.universe._
      c.Expr[IO[Assertion]](q"_root_.cats.effect.IO.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
