package com.github.pshirshov.izumi.idealingua.runtime.rpc

trait IRTDispatcher[Or[+_, +_]] {
  def dispatch(input: IRTMuxRequest): Or[Throwable, IRTMuxResponse]
}
