package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawTypeDef, _}

object IL {

  sealed trait Val

  final case class ILDomainId(id: DomainId) extends Val

  final case class ILService(v: Service) extends Val

  final case class ILInclude(i: String) extends Val

  final case class ILDef(v: RawTypeDef) extends Val

}
