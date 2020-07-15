package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertIO.AssertIOMacro
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro
import zio.IO

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait AssertIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): IO[Nothing, Assertion] = macro AssertIOMacro.impl
}

object AssertIO extends AssertIO {

  object AssertIOMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[IO[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[IO[Nothing, Assertion]](q"_root_.zio.IO.effectTotal(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
