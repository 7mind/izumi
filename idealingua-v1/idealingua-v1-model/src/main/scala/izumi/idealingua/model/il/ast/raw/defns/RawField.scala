package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.AbstractIndefiniteId

final case class RawField(typeId: AbstractIndefiniteId, name: Option[String], meta: RawNodeMeta)
