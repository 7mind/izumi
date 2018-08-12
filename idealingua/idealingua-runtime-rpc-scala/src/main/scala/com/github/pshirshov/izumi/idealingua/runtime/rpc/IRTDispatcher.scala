package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

trait IRTDispatcher {
  def dispatch(input: IRTMuxRequest): IO[Throwable, IRTMuxResponse]
}
