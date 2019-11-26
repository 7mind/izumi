package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.TypeId.ServiceId

final case class Service(id: ServiceId, methods: List[DefMethod], meta: NodeMeta)
