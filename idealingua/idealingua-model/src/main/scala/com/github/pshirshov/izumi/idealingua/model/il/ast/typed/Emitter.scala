package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.EmitterId

final case class Emitter(id: EmitterId, events: List[DefMethod], doc: NodeMeta)


