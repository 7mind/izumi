package com.github.pshirshov.izumi.idealingua.model.finaldef

case class DomainDefinition(id: DomainId, types: Seq[FinalDefinition], services: Seq[Service])
