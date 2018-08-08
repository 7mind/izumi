package com.github.pshirshov.izumi.r2.idealingua.test.impls

import com.github.pshirshov.izumi.idealingua.runtime.rpc._
import com.github.pshirshov.izumi.r2.idealingua.test.generated._

import scala.language.higherKinds

trait AbstractGreeterServer[R[_], C] extends GreeterService[R, C] with IRTWithResult[R] {
  override def greet(ctx: C, name: String, surname: String): Result[String] = _Result {
    s"Hi, $name $surname!"
  }

  override def sayhi(ctx: C): Result[String] = _Result {
    s"Hi!"
  }
}

object AbstractGreeterServer {

  class Impl[R[_] : IRTResult, C] extends AbstractGreeterServer[R, C] {
    override protected def _ServiceResult: IRTResult[R] = implicitly
  }

}

trait AbstractCalculatorServer[R[_], C] extends CalculatorService[R, C] with IRTWithResult[R] {

  override def sum(ctx: C, a: Int, b: Int): Result[Int] = _Result {
    a + b
  }
}

object AbstractCalculatorServer {

  class Impl[R[_] : IRTResult, C] extends AbstractCalculatorServer[R, C] {
    override protected def _ServiceResult: IRTResult[R] = implicitly
  }

}
