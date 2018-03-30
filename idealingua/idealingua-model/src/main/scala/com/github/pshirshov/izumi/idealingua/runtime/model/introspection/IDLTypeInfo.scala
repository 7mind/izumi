package com.github.pshirshov.izumi.idealingua.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

case class IDLTypeInfo(typeId: TypeId, domain: IDLDomainCompanion, signature: Int)
