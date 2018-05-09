package com.github.pshirshov.izumi.idealingua.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

final case class IDLTypeInfo(typeId: TypeId, domain: IDLDomainCompanion, signature: Int)
