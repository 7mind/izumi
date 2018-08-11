package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds


abstract class IRTMethodWrapper[R[_, _], C] extends IRTResult[R] {
  val signature: IRTMethodSignature
  val marshaller: IRTMarshaller[R]

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]
}



