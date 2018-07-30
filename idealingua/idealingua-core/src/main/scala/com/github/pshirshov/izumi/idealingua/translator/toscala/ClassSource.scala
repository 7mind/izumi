package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ServiceContext, ServiceMethodProduct}

sealed trait ClassSource {
  def nonEphemeral: TypeId
}

object ClassSource {
  final case class CsDTO(dto: DTO) extends ClassSource {
    override def nonEphemeral: TypeId = dto.id
  }
  final case class CsInterface(i: Interface) extends ClassSource {
    override def nonEphemeral: TypeId = i.id
  }
  final case class CsMethodInput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource {
    override def nonEphemeral: TypeId = smp.inputIdWrapped
  }
  final case class CsMethodOutput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource {
    override def nonEphemeral: TypeId = smp.outputIdWrapped
  }
}
