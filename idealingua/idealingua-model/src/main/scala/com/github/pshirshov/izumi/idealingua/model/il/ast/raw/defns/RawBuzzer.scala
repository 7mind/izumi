package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName

final case class RawBuzzer(id: RawDeclaredTypeName, events: List[RawMethod], meta: RawNodeMeta)
