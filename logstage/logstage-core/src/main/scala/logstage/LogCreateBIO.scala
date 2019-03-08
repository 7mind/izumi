package logstage

object LogCreateBIO {
  def apply[F[_, _]: LogCreateBIO]: LogCreateBIO[F] = implicitly
}
