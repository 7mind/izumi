package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import org.http4s.AuthedRequest

trait WithHttp4sHttpRequestContext {
  this: Http4sContext =>

  case class HttpRequestContext[Ctx](request: AuthedRequest[CIO, Ctx], context: Ctx)
}
