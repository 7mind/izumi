//package izumi.r2.idealingua.test.impls
//
//import cats.MonadError
//import cats.data.EitherT
//import izumi.idealingua.runtime.rpc._
//import izumi.r2.idealingua.test.generated._
//
//import scala.language.higherKinds
//
//
//
//trait AbstractGreeterServerMonomorphicForeign[C]
//  extends GreeterServiceServer[EitherT[cats.effect.IO, ?, ?], C] {
//  val R: EitherTResult.type = EitherTResult
//
//  protected implicit def ME[E]: MonadError[Or[E, ?], E] = R.ME[E]
//
//  override def greet(ctx: C, name: String, surname: String): Just[String] = just {
//    s"Hi, $name $surname!"
//  }
//
//  override def sayhi(ctx: C): Just[String] = just {
//    "Hi!"
//  }
//
//  override def alternative(ctx: C): Or[Long, String] = choice {
//    /*
//    ME[Long].raiseError(45)
//    ME[Long].pure("test")
//    */
//    Right("value")
//  }
//
//  override def nothing(ctx: C): Or[Nothing, String] = just {
//    ""
//  }
//}
//
//object AbstractGreeterServerMonomorphicForeign {
//    class ImplEitherT[C] extends AbstractGreeterServer[EitherT[cats.effect.IO, ?, ?], C]
//
//    class ImplForeignProxy[F[_, _] : IRTResult, C](proxied: ImplEitherT[C]) extends GreeterServiceServer[F, C] {
//      override def greet(ctx: C, name: String, surname: String): Just[String] = {
//        proxied.greet(ctx, name, surname)
//      }
//
//      override def sayhi(ctx: C): Just[String] = ???
//
//      override def nothing(ctx: C): Just[String] = ???
//
//      override def alternative(ctx: C): R[Long, String] = ???
//    }
//
//}
