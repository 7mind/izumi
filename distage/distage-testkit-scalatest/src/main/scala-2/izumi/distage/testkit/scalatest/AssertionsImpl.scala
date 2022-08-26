package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import izumi.functional.bio.{IO2, IO3}
import org.scalactic.source.Position
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion
import org.scalatest.distage.DistageAssertionsMacro

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait AssertCIOImpl { this: AssertCIO =>
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): cats.effect.IO[Assertion] = macro AssertCIOImpl.AssertCIOMacro.impl
}

object AssertCIOImpl {
  object AssertCIOMacro {
    def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[cats.effect.IO[Assertion]] = {
      import c.universe.*
      c.Expr[cats.effect.IO[Assertion]](q"_root_.cats.effect.IO.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
    }
  }
}

trait AssertIO2Impl[F[+_, +_]] { this: AssertIO2[F] =>
  def assertIO(arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]
}

trait AssertIO2StaticImpl {
  def assertIO[F[+_, +_]](arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = macro AssertIO2Macro.impl[F]
}

object AssertIO2Macro {
  def impl[F[+_, +_]](
                       c: blackbox.Context
                     )(arg: c.Expr[Boolean]
                     )(IO2: c.Expr[IO2[F]],
                       prettifier: c.Expr[Prettifier],
                       pos: c.Expr[org.scalactic.source.Position],
                     ): c.Expr[F[Nothing, Assertion]] = {
    import c.universe.*
    c.Expr[F[Nothing, Assertion]](q"$IO2.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
  }
}

trait AssertIO3Impl[F[-_, +_, +_]] { this: AssertIO3[F] =>
  final def assertIO(arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] = macro AssertIO3Macro.impl[F]
}
trait AssertIO3StaticImpl {
  final def assertIO[F[-_, +_, +_]](
    arg: Boolean
  )(implicit IO3: IO3[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Any, Nothing, Assertion] = macro AssertIO3Macro.impl[F]

}

object AssertIO3Macro {
  def impl[F[-_, +_, +_]](
    c: blackbox.Context
  )(arg: c.Expr[Boolean]
  )(IO3: c.Expr[IO3[F]],
    prettifier: c.Expr[Prettifier],
    pos: c.Expr[org.scalactic.source.Position],
  ): c.Expr[F[Any, Nothing, Assertion]] = {
    import c.universe._
    c.Expr[F[Any, Nothing, Assertion]](q"$IO3.sync(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
  }
}

trait AssertSyncImpl[F[_]] { this: AssertSync[F] =>
  final def assertIO(arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = macro AssertSyncMacro.impl[F]
}

trait AssertSyncStaticImpl {
  final def assertIO[F[_]](arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] = macro AssertSyncMacro.impl[F]

}

object AssertSyncMacro {
  def impl[F[_]](
                  c: blackbox.Context
                )(arg: c.Expr[Boolean]
                )(Sync: c.Expr[Sync[F]],
                  prettifier: c.Expr[Prettifier],
                  pos: c.Expr[org.scalactic.source.Position],
                ): c.Expr[F[Assertion]] = {
    import c.universe._
    c.Expr[F[Assertion]](q"$Sync.delay(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
  }
}

trait AssertZIOImpl { this: AssertZIO =>
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): zio.IO[Nothing, Assertion] = macro AssertZIOMacro.impl
}

object AssertZIOMacro {
  def impl(c: blackbox.Context)(arg: c.Expr[Boolean])(prettifier: c.Expr[Prettifier], pos: c.Expr[Position]): c.Expr[zio.IO[Nothing, Assertion]] = {
    import c.universe._
    c.Expr[zio.IO[Nothing, Assertion]](q"_root_.zio.IO.effectTotal(${DistageAssertionsMacro.assert(c)(arg)(prettifier, pos)})")
  }
}
