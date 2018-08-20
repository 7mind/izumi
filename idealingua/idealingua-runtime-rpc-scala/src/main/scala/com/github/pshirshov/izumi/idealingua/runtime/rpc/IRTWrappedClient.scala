package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTWrappedClient {
  def allCodecs: Map[IRTMethodId, IRTCirceMarshaller]
}
