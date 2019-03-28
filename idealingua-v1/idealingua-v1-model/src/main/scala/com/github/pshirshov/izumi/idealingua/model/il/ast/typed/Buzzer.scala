package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{BuzzerId, ServiceId}

final case class Buzzer(id: BuzzerId, events: List[DefMethod], meta: NodeMeta) {
  def asService = Service(ServiceId(id.domain, id.name), events, meta)
}


