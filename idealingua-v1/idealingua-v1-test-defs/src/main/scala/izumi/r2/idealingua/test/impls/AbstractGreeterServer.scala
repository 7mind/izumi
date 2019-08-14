package izumi.r2.idealingua.test.impls

import izumi.functional.bio.BIO
import izumi.r2.idealingua.test.generated._

abstract class AbstractGreeterServer[R[+_, +_] : BIO, C]
  extends GreeterServiceServer[R, C] {

  val R: BIO[R] = implicitly

  import R._

  override def greet(ctx: C, name: String, surname: String): Just[String] = pure {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Just[String] = pure {
    "Hi!"
  }

  override def alternative(ctx: C): Or[Long, String] = fromEither {
    Right("value")
  }

  override def nothing(ctx: C): Or[Nothing, String] = pure {
    ""
  }
}

object AbstractGreeterServer {
  class Impl[R[+_, +_] : BIO, C] extends AbstractGreeterServer[R, C]
}
