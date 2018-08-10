package com.github.pshirshov.izumi.idealingua.runtime.rpc

import io.circe.{DecodingFailure, Json}
import scalaz.zio.IO




abstract class IRTMethodWrapper[C]() extends IRTZioResult {
  val signature: IRTMethodSignature
  val marshaller: IRTMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]
}



