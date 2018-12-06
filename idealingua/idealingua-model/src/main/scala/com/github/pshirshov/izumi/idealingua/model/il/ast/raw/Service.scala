package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

final case class Service(id: ServiceId, methods: List[RawMethod], meta: RawNodeMeta) extends RawWithMeta {
  override def updateMeta(f: RawNodeMeta => RawNodeMeta): RawWithMeta = this.copy(meta = f(meta))
}









