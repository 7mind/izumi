package izumi.distage.testkit.scalatest

import izumi.distage.testkit.scalatest.AssertMonix.AssertMonixMacro
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** scalatest assertion macro for [[monix.eval.Task]] */
trait AssertMonix {
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): monix.eval.Task[Assertion] = macro AssertMonixMacro.impl
}

object AssertMonix extends AssertMonix {

  object AssertMonixMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[monix.eval.Task[Assertion]] = {
      import c.universe._
      c.Expr[monix.eval.Task[Assertion]](q"_root_.monix.eval.Task.eval(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }

}
