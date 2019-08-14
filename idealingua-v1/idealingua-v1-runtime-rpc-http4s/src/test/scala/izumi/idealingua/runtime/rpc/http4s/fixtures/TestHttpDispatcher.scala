package izumi.idealingua.runtime.rpc.http4s.fixtures

import izumi.idealingua.runtime.rpc.{IRTMuxRequest, IRTMuxResponse}

trait TestHttpDispatcher extends TestDispatcher {
  type BiIO[+E, +V] = zio.IO[E, V]

  def sendRaw(request: IRTMuxRequest, body: Array[Byte]): BiIO[Throwable, IRTMuxResponse]

}
