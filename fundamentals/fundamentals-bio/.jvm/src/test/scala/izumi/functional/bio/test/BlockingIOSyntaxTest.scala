package izumi.functional.bio.test

import izumi.functional.bio.{BIOFunctor, BlockingIO, BlockingIO3, F}
import izumi.fundamentals.bio.test.masking.BIOMonad3
import zio.ZIO
import zio.blocking.Blocking

object BlockingIOSyntaxTest {

  def `attach BlockingIO methods to a trifunctor BIO`[F[-_, +_, +_]: BIOMonad3: BlockingIO3]: F[Any, Throwable, Int] = {
    F.syncBlocking(2)
  }
  def `attach BlockingIO methods to a bifunctor BIO`[F[+_, +_]: BIOFunctor: BlockingIO]: F[Throwable, Int] = {
    F.syncBlocking(2)
  }
  val _: ZIO[Blocking, Throwable, Int] = {
    implicit val b: zio.blocking.Blocking = zio.blocking.Blocking.Live
    `attach BlockingIO methods to a trifunctor BIO`[BlockingIO3.ZIOBlocking#l]
    `attach BlockingIO methods to a bifunctor BIO`[zio.IO]
  }
}
