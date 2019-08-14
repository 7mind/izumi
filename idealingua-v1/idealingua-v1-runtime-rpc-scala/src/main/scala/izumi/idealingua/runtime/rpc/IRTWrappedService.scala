package izumi.idealingua.runtime.rpc

trait IRTWrappedService[R[_, _], -C] { self =>
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, IRTMethodWrapper[R, C]]

  final def contramap[D](f: D => C): IRTWrappedService[R, D] = {
    new IRTWrappedService[R, D] {
      override final val serviceId: IRTServiceId = self.serviceId
      override final val allMethods: Map[IRTMethodId, IRTMethodWrapper[R, D]] = {
        self.allMethods.map { case (k, v) => k -> v.contramap(f) }
      }
    }
  }
}
