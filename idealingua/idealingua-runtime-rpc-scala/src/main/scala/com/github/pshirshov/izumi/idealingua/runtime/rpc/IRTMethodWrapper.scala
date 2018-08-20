package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds


abstract class IRTMethodWrapper[Or[_, _], C] {
  type Just[T] = Or[Nothing, T]

  val signature: IRTMethodSignature
  val marshaller: IRTCirceMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]
}



