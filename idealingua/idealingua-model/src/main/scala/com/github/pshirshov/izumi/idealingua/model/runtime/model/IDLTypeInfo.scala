package com.github.pshirshov.izumi.idealingua.model.runtime.model

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

case class IDLTypeInfo(typeId: TypeId, domain: IDLDomainCompanion, signature: Int)
