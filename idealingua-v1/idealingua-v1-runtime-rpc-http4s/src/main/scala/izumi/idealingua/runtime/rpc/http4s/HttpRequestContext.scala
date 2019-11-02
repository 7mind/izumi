package izumi.idealingua.runtime.rpc.http4s

import org.http4s.AuthedRequest

// we can't make it a case class, see https://github.com/scala/bug/issues/11239
class HttpRequestContext[F[_], Ctx](val request: AuthedRequest[F, Ctx], val context: Ctx)
