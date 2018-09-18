package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s.fixtures

import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTMuxRequest, IRTMuxResponse}

trait TestHttpDispatcher extends TestDispatcher {
  type CatsIO[+T] = cats.effect.IO[T]

  def sendRaw(request: IRTMuxRequest, body: Array[Byte]): BiIO[Throwable, IRTMuxResponse]

}
