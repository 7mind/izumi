package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{EmitterId, StreamsId}

final case class Emitter(id: EmitterId, events: List[RawMethod], doc: Option[String])




