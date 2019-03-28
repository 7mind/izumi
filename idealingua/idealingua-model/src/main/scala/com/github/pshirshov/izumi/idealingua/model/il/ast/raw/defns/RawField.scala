package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawRef

final case class RawField(typeId: RawRef, name: Option[String], meta: RawNodeMeta)
