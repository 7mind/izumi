package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.EmitterId

final case class Emitter(id: EmitterId, events: List[RawMethod], meta: RawNodeMeta)




