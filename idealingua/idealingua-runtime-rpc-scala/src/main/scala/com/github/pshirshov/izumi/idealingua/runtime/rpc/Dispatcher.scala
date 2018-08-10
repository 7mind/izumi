package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scalaz.zio.IO

trait Dispatcher {
  def dispatch(input: IRTMuxRequest[Product]): IO[Throwable, IRTMuxResponse[Product]]
}
