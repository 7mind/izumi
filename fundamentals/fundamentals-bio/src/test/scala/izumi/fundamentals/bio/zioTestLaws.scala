package izumi.fundamentals.bio

import cats.effect.{Concurrent, ContextShift}
import cats.effect.laws.ConcurrentLaws
import cats.effect.laws.discipline.ConcurrentTests
import cats.effect.laws.util.TestContext
import org.scalacheck.{Arbitrary, Cogen, Gen}
import zio.{Runtime, ZIO}

import scala.concurrent.ExecutionContext

class zioTestLaws extends MiniBIOCatsLaws with GenArbEffects {

  implicit def zioTaskRnd[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[zio.IO[E, A]] =
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A])))

  implicit val ctx = TestContext()

  import izumi.functional.bio.catz._
  val concurrentTestZio = new ConcurrentTests[zio.Task] {
    val laws = new ConcurrentLaws[zio.Task] {
      val F = Concurrent[zio.Task]

      private[this] final val zioContextShift0: ContextShift[ZIO[Any, Any, *]] =
        new ContextShift[ZIO[Any, Any, *]] {
          override final def shift: ZIO[Any, Any, Unit]                                              = ZIO.yieldNow
          override final def evalOn[A](ec: ExecutionContext)(fa: ZIO[Any, Any, A]): ZIO[Any, Any, A] = fa.on(ec)
        }

      implicit final def zioContextShift[R, E]: ContextShift[ZIO[R, E, *]] =
        zioContextShift0.asInstanceOf[ContextShift[ZIO[R, E, *]]]

      val contextShift = ContextShift[zio.Task]
    }
  }

  checkAll("ConcurrentZIO", concurrentTestZio.sync[Int, Int, Int])
}
