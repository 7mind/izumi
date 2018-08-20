package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

import scala.language.higherKinds


abstract class IRTMethodWrapper[R[_, _] : IRTResult, C] {
  val R: IRTResult[R] = implicitly

  val signature: IRTMethodSignature
  val marshaller: IRTCirceMarshaller[R]

  def invoke(ctx: C, input: signature.Input): R.Just[signature.Output]
}



