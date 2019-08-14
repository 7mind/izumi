package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.StreamsId

final case class RawStreams(id: StreamsId, streams: List[RawStream], meta: RawNodeMeta)
