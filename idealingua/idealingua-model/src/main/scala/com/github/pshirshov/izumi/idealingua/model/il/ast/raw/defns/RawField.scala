package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId

final case class RawField(typeId: AbstractIndefiniteId, name: Option[String], meta: RawNodeMeta)
