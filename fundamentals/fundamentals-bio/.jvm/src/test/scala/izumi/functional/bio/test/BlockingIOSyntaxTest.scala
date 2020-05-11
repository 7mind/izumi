package izumi.functional.bio.test

import izumi.functional.bio.{BIOFunctor, BIOMonad3, BlockingIO, BlockingIO3, BlockingIOInstances, F}
import org.scalatest.wordspec.AnyWordSpec
import zio.blocking.Blocking
import zio.{Has, ZIO}

class BlockingIOSyntaxTest extends AnyWordSpec {

  def `attach BlockingIO methods to a trifunctor BIO`[F[-_, +_, +_]: BIOMonad3: BlockingIO3]: F[Any, Throwable, Int] = {
    F.syncBlocking(2)
  }
  def `attach BlockingIO methods to a bifunctor BIO`[F[+_, +_]: BIOFunctor: BlockingIO]: F[Throwable, Int] = {
    F.syncBlocking(2)
  }
  locally {
    val _: ZIO[Blocking, Throwable, Int] = {
      implicit val blocking: Blocking = Has(Blocking.Service.live)
      `attach BlockingIO methods to a trifunctor BIO`[ZIO]
      `attach BlockingIO methods to a bifunctor BIO`[zio.IO]
      `attach BlockingIO methods to a bifunctor BIO`[BlockingIOInstances.ZIOWithBlocking[Any, +?, +?]]
    }
  }

  "BlockingIO.apply is callable" in {
    class X[F[+_, +_]: BlockingIO] {
      def hello = BlockingIO[F].syncBlocking(println("hello world!"))
    }
    class X3[F[-_, +_, +_]: BlockingIO3] {
      def hello = BlockingIO3[F].syncBlocking(println("hello world!"))
    }

    assert(new X[zio.ZIO[Blocking, +?, +?]].hello != null)
    locally {
      implicit val blocking: Blocking = Has(Blocking.Service.live)
      assert(new X[zio.IO].hello != null)
    }
    assert(new X3[BlockingIOInstances.ZIOWithBlocking].hello != null)
  }

}
