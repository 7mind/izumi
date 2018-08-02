package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{EmitterId, ServiceId}

final case class Service(id: ServiceId, methods: List[DefMethod], doc: Option[String])







