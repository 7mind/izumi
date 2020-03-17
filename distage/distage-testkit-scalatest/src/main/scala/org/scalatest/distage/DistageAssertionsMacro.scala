package org.scalatest.distage

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.{Assertion, AssertionsMacro}

import scala.reflect.macros.{blackbox, whitebox}

object DistageAssertionsMacro {
  def assert(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[Assertion] = {
    // Force scalatest's `assert` to run in `blackbox` Context
    // The casts don't fail because at runtime there's actually only one `Context`
    val c0 = c.asInstanceOf[whitebox.Context]
    AssertionsMacro
      .assert(
        context = c.asInstanceOf[c0.type]
      )(condition = arg.asInstanceOf[c0.Expr[Boolean]])(
        prettifier = prettifier.asInstanceOf[c0.Expr[Prettifier]],
        pos        = pos.asInstanceOf[c0.Expr[Position]],
      ).asInstanceOf[c.Expr[Assertion]]
  }
}
