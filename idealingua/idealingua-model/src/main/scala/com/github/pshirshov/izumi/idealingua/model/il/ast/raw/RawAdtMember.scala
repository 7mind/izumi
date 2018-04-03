package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, TypeName}

case class RawAdtMember(typeId: AbstractTypeId, memberName: Option[TypeName])
