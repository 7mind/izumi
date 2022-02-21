package izumi.functional.bio.test

import cats.effect.kernel
import izumi.functional.bio.catz.*
import izumi.functional.bio.{F, IO2}
import org.scalatest.wordspec.AnyWordSpec

class CatsConversionTest extends AnyWordSpec {

  class X[F[+_, +_]: IO2](val ref: kernel.Ref[F[Throwable, _], Int])
//
//  "pickup conversion to Sync" in {
//    def conv[F[+_, +_]: IO2]: F[Throwable, Int] = {
//      Ref
//        .of(0)
//        .flatMap(_.get)
//    }
//    conv[zio.IO]
//    implicitly[Sync[zio.Task]]
//    implicitly[Async[zio.Task]]
//    implicitly[Parallel[zio.Task]]
//    implicitly[Concurrent[zio.Task]]
//  }

  "pickup conversion to Monad" in {
    def c1[F[+_, +_]: IO2]: F[Nothing, Unit] = {
      import cats.syntax.applicative.*
      import cats.syntax.monad.*

      ().iterateWhileM(_ => ().pure)(_ => true)
    }
    def c2[F[+_, +_]: IO2]: F[Nothing, List[Unit]] = {
      import cats.syntax.all.*

      List(1, 2, 3).traverseFilter {
        case 2 => F.pure(Some(()))
        case _ => F.pure(None)
      }
    }

    c1[zio.IO]
    c2[zio.IO]
  }
}
