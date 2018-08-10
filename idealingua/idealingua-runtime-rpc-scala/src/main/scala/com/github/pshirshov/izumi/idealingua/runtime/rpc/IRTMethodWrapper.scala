package com.github.pshirshov.izumi.idealingua.runtime.rpc




abstract class IRTMethodWrapper[C]() extends IRTZioResult {
  val signature: IRTMethodSignature
  val marshaller: IRTMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]
}



