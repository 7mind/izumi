package izumi.distage.testkit.scalatest

import izumi.functional.bio.{IO2, IO3}
import org.scalactic.source.Position
import org.scalactic.{Prettifier, source}
import org.scalatest.{Assertion, Assertions, AssertionsMacro}
import cats.effect.kernel.Sync

trait AssertCIOImpl { this: AssertCIO =>
  final inline def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): cats.effect.IO[Assertion] =
    _root_.cats.effect.IO.delay(Assertions.assert(arg))
}

trait AssertIO2Impl[F[+_, +_]] { this: AssertIO2[F] =>
  final def assertIO(arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] = IO2.sync(Assertions.assert(arg))
}

trait AssertIO2StaticImpl {
  final def assertIO[F[+_, +_]](arg: Boolean)(implicit IO2: IO2[F], prettifier: Prettifier, pos: source.Position): F[Nothing, Assertion] =
    IO2.sync(Assertions.assert(arg))
}

trait AssertIO3Impl[F[-_, +_, +_]] { this: AssertIO3[F] =>
  final def assertIO(arg: Boolean)(implicit IO3: IO3[F], prettifier: Prettifier, pos: source.Position): F[Any, Nothing, Assertion] =
    IO3.sync(Assertions.assert(arg))
}
trait AssertIO3StaticImpl {
  final def assertIO[F[-_, +_, +_]](
    arg: Boolean
  )(implicit IO3: IO3[F],
    prettifier: Prettifier,
    pos: source.Position,
  ): F[Any, Nothing, Assertion] = IO3.sync(Assertions.assert(arg))

}

trait AssertSyncImpl[F[_]] { this: AssertSync[F] =>
  final def assertIO(arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] =
    Sync.delay(Assertions.assert(arg))
}

trait AssertSyncStaticImpl {
  final def assertIO[F[_]](arg: Boolean)(implicit Sync: Sync[F], prettifier: Prettifier, pos: source.Position): F[Assertion] =
    Sync.delay(Assertions.assert(arg))

}
trait AssertZIOImpl { this: AssertZIO =>
  final def assertIO(arg: Boolean)(implicit prettifier: Prettifier, pos: Position): zio.IO[Nothing, Assertion] =
    zio.IO.effectTotal(Assertions.assert(arg))
}
