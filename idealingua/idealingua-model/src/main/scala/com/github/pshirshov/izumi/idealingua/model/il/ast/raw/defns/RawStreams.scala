package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName

final case class RawStreams(id: RawDeclaredTypeName, streams: List[RawStream], meta: RawNodeMeta)
