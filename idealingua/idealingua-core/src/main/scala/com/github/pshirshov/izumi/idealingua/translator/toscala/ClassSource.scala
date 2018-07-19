package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ServiceContext, ServiceMethodProduct}

sealed trait ClassSource

object ClassSource {
  final case class CsDTO(dto: DTO) extends ClassSource
  final case class CsInterface(i: Interface) extends ClassSource
  final case class CsMethodInput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource
  final case class CsMethodOutput(sc: ServiceContext, smp: ServiceMethodProduct) extends ClassSource
}
