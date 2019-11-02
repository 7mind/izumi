package izumi.idealingua.runtime.rpc

trait IRTDispatcher[F[_, _]] {
  def dispatch(input: IRTMuxRequest): F[Throwable, IRTMuxResponse]
}
