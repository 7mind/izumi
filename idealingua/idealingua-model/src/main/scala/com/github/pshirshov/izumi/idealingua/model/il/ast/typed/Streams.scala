package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.StreamsId

final case class Streams(id: StreamsId, streams: List[TypedStream], doc: NodeMeta)
