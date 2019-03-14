package com.github.pshirshov.izumi.idealingua.runtime.rpc

abstract class IRTMethodWrapper[R[_, _], -C] { self =>
  final type Just[T] = R[Nothing, T]

  val signature: IRTMethodSignature
  val marshaller: IRTCirceMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]

  final def contramap[D](f: D => C): IRTMethodWrapper[R, D] = {
    new IRTMethodWrapper[R, D] {
      override final val signature: self.signature.type = self.signature
      override final val marshaller: self.marshaller.type = self.marshaller
      override final def invoke(ctx: D, input: self.signature.Input): R[Nothing, self.signature.Output] = {
        self.invoke(f(ctx), input)
      }
    }
  }
}
