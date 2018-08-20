package com.github.pshirshov.izumi.r2.idealingua.test.impls

import cats.MonadError
import cats.data.EitherT
import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated._
import IRTResultTransZio._

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

  class ImplEitherT[C] extends AbstractGreeterServer1[EitherT[cats.effect.IO, ?, ?], C]

}

trait AbstractGreeterServerMonomorphicForeign[C]
  extends GreeterServiceServer[EitherT[cats.effect.IO, ?, ?], C] {
  val R: EitherTResult.type = EitherTResult

  import R._

  protected implicit def ME[E]: MonadError[Or[E, ?], E] = R.ME[E]

  override def greet(ctx: C, name: String, surname: String): Just[String] = just {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Just[String] = just {
    "Hi!"
  }

  override def alternative(ctx: C): Or[Long, String] = choice {
    /*
    ME[Long].raiseError(45)
    ME[Long].pure("test")
    */
    Right("value")
  }

  override def nothing(ctx: C): Or[Nothing, String] = just {
    ""
  }
}

object AbstractGreeterServerMonomorphicForeign {


}
