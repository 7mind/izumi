package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId

final case class Service(id: ServiceId, methods: List[DefMethod], doc: NodeMeta)







