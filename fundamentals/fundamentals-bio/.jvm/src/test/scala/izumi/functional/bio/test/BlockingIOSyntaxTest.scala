package izumi.functional.bio.test

import izumi.functional.bio.{BlockingIO2, BlockingIO3, BlockingIOInstances, F, Functor2, Monad3}
import org.scalatest.wordspec.AnyWordSpec
import zio.blocking.Blocking
import zio.{Has, IO, ZIO}

class BlockingIOSyntaxTest extends AnyWordSpec {

  def `attach BlockingIO methods to a trifunctor BIO`[F[-_, +_, +_]: Monad3: BlockingIO3]: F[Any, Throwable, Int] = {
    F.syncBlocking(2)
  }
  def `attach BlockingIO methods to a bifunctor BIO`[F[+_, +_]: Functor2: BlockingIO2]: F[Throwable, Int] = {
    F.syncBlocking(2)
  }
  locally {
    val _: ZIO[Blocking, Throwable, Int] = {
      implicit val blocking: Blocking = Has(Blocking.Service.live)
      `attach BlockingIO methods to a trifunctor BIO`[ZIO]
      `attach BlockingIO methods to a bifunctor BIO`[IO]
      `attach BlockingIO methods to a bifunctor BIO`[ZIO[Blocking, +_, +_]]
    }
  }

  "BlockingIO.apply is callable" in {
    class X[F[+_, +_]: BlockingIO2] {
      def hello = BlockingIO2[F].syncBlocking(println("hello world!"))
    }
    class X3[F[-_, +_, +_]: BlockingIO3] {
      def hello = BlockingIO3[F].syncBlocking(println("hello world!"))
    }
    def zioBlockingApply2(): ZIO[Blocking, Throwable, Unit] = BlockingIO2.apply.syncBlocking(())
    def zioBlockingApply3(): ZIO[Blocking, Throwable, Unit] = BlockingIO3.apply.syncBlocking(())

    assert(new X[zio.ZIO[Blocking, +_, +_]].hello != null)
    locally {
      implicit val blocking: Blocking = Has(Blocking.Service.live)
      assert(new X[IO].hello != null)
    }
    assert(new X3[BlockingIOInstances.ZIOWithBlocking]().hello != null)

    def summonOrNull[A](implicit a: A = null): A = a
    assert(summonOrNull[BlockingIO2[IO]] == null)

    assert(zioBlockingApply2() != null)
    assert(zioBlockingApply3() != null)
  }

}
