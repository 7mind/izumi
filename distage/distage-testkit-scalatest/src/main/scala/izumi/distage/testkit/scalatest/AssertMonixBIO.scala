package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertMonixBIO.AssertMonixBIOMacro
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[monix.bio.IO]] */
trait AssertMonixBIO {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): monix.bio.IO[Nothing, Assertion] = macro AssertMonixBIOMacro.impl
}

object AssertMonixBIO extends AssertMonixBIO {

  object AssertMonixBIOMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[monix.bio.IO[Nothing, Assertion]] = {
      import c.universe._
      c.Expr[monix.bio.IO[Nothing, Assertion]](q"_root_.monix.bio.IO.evalTotal(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
