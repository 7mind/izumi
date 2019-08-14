package izumi.idealingua.runtime.rpc

trait IRTDispatcher[R[_, _]] {
  def dispatch(input: IRTMuxRequest): R[Throwable, IRTMuxResponse]
}
