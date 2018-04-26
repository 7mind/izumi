package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds


trait IRTWithResultType[R[_]] {
  type Result[T] = R[T]
}

trait IRTWithResult[R[_]] extends IRTWithResultType[R] {
  protected def _ServiceResult: IRTServiceResult[R]

  protected def _Result[T](value: => T): R[T] = _ServiceResult.wrap(value)
}

trait IRTWithContext[C] {
  type Context = C
}
