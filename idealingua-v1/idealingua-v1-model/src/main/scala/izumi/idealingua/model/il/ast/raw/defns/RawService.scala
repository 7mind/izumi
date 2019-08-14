package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.TypeId.ServiceId

final case class RawService(id: ServiceId, methods: List[RawMethod], meta: RawNodeMeta)
