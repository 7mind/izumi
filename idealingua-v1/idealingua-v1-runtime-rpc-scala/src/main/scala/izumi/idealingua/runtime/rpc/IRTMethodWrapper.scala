package izumi.idealingua.runtime.rpc

abstract class IRTMethodWrapper[F[_, _], -C] { self =>
  final type Just[T] = F[Nothing, T]

  val signature: IRTMethodSignature
  val marshaller: IRTCirceMarshaller

  def invoke(ctx: C, input: signature.Input): Just[signature.Output]

  final def contramap[D](f: D => C): IRTMethodWrapper[F, D] = {
    new IRTMethodWrapper[F, D] {
      override final val signature: self.signature.type = self.signature
      override final val marshaller: self.marshaller.type = self.marshaller
      override final def invoke(ctx: D, input: self.signature.Input): F[Nothing, self.signature.Output] = {
        self.invoke(f(ctx), input)
      }
    }
  }
}
