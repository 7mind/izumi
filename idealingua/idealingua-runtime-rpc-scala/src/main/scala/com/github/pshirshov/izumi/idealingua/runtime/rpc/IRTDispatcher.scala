package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTDispatcher[Or[+_, +_]] {
  def dispatch(input: IRTMuxRequest): Or[Throwable, IRTMuxResponse]
}
