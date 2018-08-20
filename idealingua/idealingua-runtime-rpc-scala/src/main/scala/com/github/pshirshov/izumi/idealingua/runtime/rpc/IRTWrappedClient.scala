package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTWrappedClient[R[+_, +_]] {
  def allCodecs: Map[IRTMethodId, IRTCirceMarshaller]
}
