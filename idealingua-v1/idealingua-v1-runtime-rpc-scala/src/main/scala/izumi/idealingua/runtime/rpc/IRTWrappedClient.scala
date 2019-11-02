package izumi.idealingua.runtime.rpc

trait IRTWrappedClient {
  def allCodecs: Map[IRTMethodId, IRTCirceMarshaller]
}
