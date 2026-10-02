package izumi.fundamentals.assertions

import scala.language.experimental.macros

trait Assertions {
  def assert(condition: Boolean): Unit = macro AssertionMacro.standard
  def assert(condition: Boolean, context: AssertionContext): Unit = macro AssertionMacro.configured
  def assert1[F[_]](condition: Boolean)(implicit suspension: AssertionSuspension1[F]): F[Unit] = macro AssertionMacro.unary[F]
  def assert1[F[_]](condition: Boolean, context: AssertionContext)(implicit suspension: AssertionSuspension1[F]): F[Unit] = macro AssertionMacro.unaryConfigured[F]
  def assert2[F[_, _]](condition: Boolean)(implicit suspension: AssertionSuspension2[F]): F[Nothing, Unit] = macro AssertionMacro.binary[F]
  def assert2[F[_, _]](condition: Boolean, context: AssertionContext)(implicit suspension: AssertionSuspension2[F]): F[Nothing, Unit] = macro AssertionMacro.binaryConfigured[F]
}

object Assert extends Assertions
