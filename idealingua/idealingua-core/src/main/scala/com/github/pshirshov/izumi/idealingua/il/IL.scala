package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition._
import com.github.pshirshov.izumi.idealingua.model.il.{DomainId, FinalDefinition}

object IL {

  sealed trait Val

  case class ILDomainId(id: DomainId) extends Val

  case class ILService(v: Service) extends Val
  case class ILInclude(i: String) extends Val

  case class ILDef(v: FinalDefinition) extends Val

}
