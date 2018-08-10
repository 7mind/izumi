package com.github.pshirshov.izumi.r2.idealingua.test.impls

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated._
import scalaz.zio.IO


trait AbstractGreeterServer[C] extends GreeterServiceServer[C] with IRTResult with IRTZioResult {
  override def greet(ctx: C, name: String, surname: String): Just[String] = just {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Just[String] = just {
    s"Hi!"
  }

  override def alternative(ctx: C): Or[Long, String] = choice {
    Right("value")
  }

  override def nothing(ctx: C): IO[Nothing, String] = just {
    ""
  }
}

object AbstractGreeterServer {

  class Impl[C] extends AbstractGreeterServer[C] {}

}

//trait AbstractCalculatorServer[R[_], C] extends CalculatorService[R, C] with IRTWithResult[R] {
//
//  override def sum(ctx: C, a: Int, b: Int): Result[Int] = _Result {
//    a + b
//  }
//}
//
//object AbstractCalculatorServer {
//
//  class Impl[R[_] : IRTResult, C] extends AbstractCalculatorServer[R, C] {
//    override protected def _ServiceResult: IRTResult[R] = implicitly
//  }
//
//}
