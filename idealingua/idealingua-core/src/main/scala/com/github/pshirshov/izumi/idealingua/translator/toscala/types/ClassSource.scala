package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._

sealed trait ClassSource {
//  def nonEphemeral: TypeId
}

object ClassSource {
  final case class CsDTO(dto: DTO) extends ClassSource {
//    override def nonEphemeral: TypeId = dto.id
  }
  final case class CsInterface(i: Interface) extends ClassSource {
//    override def nonEphemeral: TypeId = i.id
  }
  final case class CsMethodInput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource {
//    override def nonEphemeral: TypeId = smp.Input.inputIdWrapped
  }
  final case class CsMethodOutput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource {
//    override def nonEphemeral: TypeId = smp.Input.outputIdWrapped
  }
}
