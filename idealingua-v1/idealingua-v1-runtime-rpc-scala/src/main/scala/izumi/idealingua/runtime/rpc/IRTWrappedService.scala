package izumi.idealingua.runtime.rpc

trait IRTWrappedService[F[_, _], -C] { self =>
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, IRTMethodWrapper[F, C]]

  final def contramap[D](f: D => C): IRTWrappedService[F, D] = {
    new IRTWrappedService[F, D] {
      override final val serviceId: IRTServiceId = self.serviceId
      override final val allMethods: Map[IRTMethodId, IRTMethodWrapper[F, D]] = {
        self.allMethods.map { case (k, v) => k -> v.contramap(f) }
      }
    }
  }
}
