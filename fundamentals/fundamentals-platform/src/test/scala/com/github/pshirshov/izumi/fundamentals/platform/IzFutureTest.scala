
package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.futures.IzFuture._
import org.scalatest.WordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class IzFutureTest extends WordSpec {

  "Extended future" should {
    "support stacking with Try()" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      assert(Await.result(Future.successful(1).safe, 1.second) == Success(1))
      val exception = new RuntimeException()
      assert(Await.result(Future.failed(exception).safe, 1.second) == Failure(exception))
    }
  }

  
}