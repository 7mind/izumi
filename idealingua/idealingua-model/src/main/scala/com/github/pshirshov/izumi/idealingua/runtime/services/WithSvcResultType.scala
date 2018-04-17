package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.language.higherKinds


trait WithSvcResultType[R[_]] {
  type Result[T] = R[T]
}

trait WithSvcResult[R[_]] extends WithSvcResultType[R] {
  protected def _ServiceResult: ServiceResult[R]

  protected def _Result[T](value: => T): R[T] = _ServiceResult.pure(value)
}

trait WithSvcContext[C] {
  type Context = C
}
