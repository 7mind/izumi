package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTWrappedService[R[_, _], C] {
  def serviceId: IRTServiceId

  def allMethods: Map[IRTMethodId, IRTMethodWrapper[R, C]]
}
