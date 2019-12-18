//package izumi.fundamentals.bio.test
//
//import cats.effect.{Async, Sync}
//import cats.effect.concurrent.Ref
//import izumi.functional.bio.BIO
//import izumi.functional.bio.F
//import izumi.functional.bio.catz._
//import org.scalatest.WordSpec
//
//class CatsConversionTest extends WordSpec {
//
//  "pickup conversion to Sync" in {
//    class X[F[+_, +_]: BIO](ref: Ref[F[Throwable, ?], Int])
//    def conv[F[+_, +_]: BIO]: F[Throwable, Int] = {
//      Ref.of(0)
//        .flatMap(_.get)
////        .flatMap(F.pure)
////        .map(new X(_))
//    }
//    conv[zio.IO]
//    implicitly[Sync[zio.Task]]
//    implicitly[Async[zio.Task]]
//  }
//
//}
