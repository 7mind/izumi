package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.language.higherKinds


trait IRTWithSvcResultType[R[_]] {
  type Result[T] = R[T]
}

trait IRTWithSvcResult[R[_]] extends IRTWithSvcResultType[R] {
  protected def _ServiceResult: IRTServiceResult[R]

  protected def _Result[T](value: => T): R[T] = _ServiceResult.wrap(value)
}

trait IRTWithSvcContext[C] {
  type Context = C
}
