package izumi.functional.bio.test

import izumi.functional.bio.{BlockingIO2, F, Functor2}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

class BlockingIOSyntaxTest extends AnyWordSpec {

  def `attach BlockingIO methods to a bifunctor BIO`[F[+_, +_]: Functor2: BlockingIO2]: F[Throwable, Int] = {
    F.syncBlocking(2)
  }
  locally {
    val _: ZIO[Any, Throwable, Int] = {
      `attach BlockingIO methods to a bifunctor BIO`[IO]
    }
  }

  "BlockingIO.apply is callable" in {
    class X[F[+_, +_]: BlockingIO2] {
      def hello = BlockingIO2[F].syncBlocking(println("hello world!"))
    }
    def zioBlockingApply2(): ZIO[Any, Throwable, Unit] = BlockingIO2.apply[zio.IO].syncBlocking(())
    def zioBlockingApply3(): ZIO[Any, Throwable, Unit] = BlockingIO2.apply.syncBlocking(())

    assert(new X[zio.ZIO[Any, +_, +_]].hello != null)
    locally {
      assert(new X[IO].hello != null)
    }

    assert(summonOrNull[BlockingIO2[IO]] != null)

    assert(zioBlockingApply2() != null)
    assert(zioBlockingApply3() != null)
  }

  def summonOrNull[A](implicit a: A = null): A = a

}
