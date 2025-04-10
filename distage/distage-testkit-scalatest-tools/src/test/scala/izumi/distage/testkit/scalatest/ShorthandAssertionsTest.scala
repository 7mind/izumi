package izumi.distage.testkit.scalatest

import cats.effect.kernel.Sync
import cats.effect.{IO => CIO}
import distage.{DefaultModule, TagK, TagKK}
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.F
import zio.{IO, ZIO}

class ShorthandAssertionsTestZIO extends SpecZIO with AssertZIO with ShorthandAssertionsZIO {

  "shorthand assertions ZIO" should {
    "support short assert versions" in {
      for {
        _ <- assertIO(ZIO.succeed(42))(_ == 42)
        _ <- assertIO(ZIO.succeed(42))(_ != 21)
        _ <- assertIO(ZIO.succeed(List("one", "two")))(_.nonEmpty)

        _ <- assertIO(ZIO.succeed(42), ZIO.succeed(21))(_ > _)
        _ <- assertIO(ZIO.succeed("test"), ZIO.succeed(4))(_.length == _)
      } yield ()
    }
  }
}

class ShorthandAssertionsTestCIO extends Spec1[CIO] with AssertCIO with ShorthandAssertionsCIO {
  "shorthand assertions CIO" should {
    "support short assert versions" in {
      for {
        _ <- assertIO(CIO.pure(42))(_ == 42)
        _ <- assertIO(CIO.pure(42))(_ != 21)
        _ <- assertIO(CIO.pure(List("one", "two")))(_.nonEmpty)

        _ <- assertIO(CIO.pure(42), CIO.pure(21))(_ > _)
        _ <- assertIO(CIO.pure("test"), CIO.pure(4))(_.length == _)
      } yield ()
    }
  }
}

abstract class ShorthandAssertionsTestBase[F[+_, +_]: TagKK: DefaultModule2] extends Spec2[F] with AssertIO2[F] with ShorthandAssertionsIO2[F]

class ShorthandAssertionsTestIO2 extends ShorthandAssertionsTestBase[IO] {
  "shorthand assertions IO2" should {
    "support short assert versions" in {
      for {
        _ <- assertIO(F.pure(42))(_ == 42)
        _ <- assertIO(F.pure(42))(_ != 21)
        _ <- assertIO(F.pure(List("one", "two")))(_.nonEmpty)

        _ <- assertIO(F.pure(42), F.pure(21))(_ > _)
        _ <- assertIO(F.pure("test"), F.pure(4))(_.length == _)
      } yield ()
    }
  }
}

abstract class ShorthandAssertionsTestSyncBase[F[_]: Sync: TagK: DefaultModule] extends Spec1[F] with AssertSync[F] with ShorthandAssertionsSync[F]

class ShorthandAssertionsTestSync extends ShorthandAssertionsTestSyncBase[CIO] {
  "shorthand assertions IO2" should {
    "support short assert versions" in {
      for {
        _ <- assertIO(CIO.pure(42))(_ == 42)
        _ <- assertIO(CIO.pure(42))(_ != 21)
        _ <- assertIO(CIO.pure(List("one", "two")))(_.nonEmpty)

        _ <- assertIO(CIO.pure(42), CIO.pure(21))(_ > _)
        _ <- assertIO(CIO.pure("test"), CIO.pure(4))(_.length == _)
      } yield ()
    }
  }
}
