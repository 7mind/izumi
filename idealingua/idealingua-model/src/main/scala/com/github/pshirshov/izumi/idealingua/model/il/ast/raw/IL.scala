package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawTypeDef.NewType

object IL {

  sealed trait Val

  final case class ILDomainId(id: DomainId) extends Val

  final case class ILInclude(i: String) extends Val

  final case class ILService(v: Service) extends Val

  final case class ILDef(v: IdentifiedRawTypeDef) extends Val

  final case class ILNewtype(v: NewType) extends Val

}
