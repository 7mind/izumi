package com.github.pshirshov.izumi.r2.idealingua.test.impls

import cats.MonadError
import cats.data.EitherT
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated._

import scala.language.higherKinds

abstract class AbstractGreeterServer1[R[_, _] : IRTResult, C]
  extends GreeterServiceServer[R, C] {

  val R: IRTResult[R] = implicitly[IRTResult[R]]

  import R._

  override def greet(ctx: C, name: String, surname: String): Just[String] = just {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Just[String] = just {
    "Hi!"
  }

  override def alternative(ctx: C): Or[Long, String] = choice {
    Right("value")
  }

  override def nothing(ctx: C): Or[Nothing, String] = just {
    ""
  }
}

object AbstractGreeterServer1 {

  class Impl[R[_, _] : IRTResult, C] extends AbstractGreeterServer1[R, C]

}

object AbstractGreeterServer2 {

  implicit object EitherTResult extends IRTResult[EitherT[cats.effect.IO, ?, ?]] {
    def ME[E]: MonadError[Or[E, ?], E] = implicitly

    def stop[V](v: => Throwable): Just[V] = ME[Nothing].point(throw v)

    def choice[E, V](v: => Either[E, V]): Or[E, V] = v match {
      case Right(r) =>
        ME[E].pure(r)

      case Left(l) =>
        ME[E].raiseError(l)
    }

    def just[V](v: => V): Just[V] = ME[Nothing].pure(v)
  }

  class Impl[C] extends AbstractGreeterServer1[EitherT[cats.effect.IO, ?, ?], C]
}
