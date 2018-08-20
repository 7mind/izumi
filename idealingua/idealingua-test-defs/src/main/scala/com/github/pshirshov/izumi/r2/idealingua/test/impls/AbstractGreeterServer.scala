package com.github.pshirshov.izumi.r2.idealingua.test.impls

import com.github.pshirshov.izumi.idealingua.runtime.bio.BIO
import com.github.pshirshov.izumi.r2.idealingua.test.generated._

import scala.language.higherKinds

abstract class AbstractGreeterServer[R[+_, +_] : BIO, C]
  extends GreeterServiceServer[R, C] {

  val R: BIO[R] = implicitly

  import R._

  override def greet(ctx: C, name: String, surname: String): Just[String] = point {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Just[String] = point {
    "Hi!"
  }

  override def alternative(ctx: C): Or[Long, String] = fromEither {
    Right("value")
  }

  override def nothing(ctx: C): Or[Nothing, String] = point {
    ""
  }
}

object AbstractGreeterServer {
  class Impl[R[+_, +_] : BIO, C] extends AbstractGreeterServer[R, C]
}
