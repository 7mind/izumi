package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{TypeDef, _}


trait IDLRenderers {

  protected implicit lazy val domainDefinition: Renderable[DomainDefinition] = new RDomain()
  protected implicit lazy val streamDirection: Renderable[StreamDirection] = RStreamDirection
  protected implicit lazy val domainId: Renderable[DomainId] = RDomainId
  protected implicit lazy val idField: Renderable[IdField] = new RIdField()
  protected implicit lazy val field: Renderable[Field] = new RField()
  protected implicit lazy val structure: Renderable[Structure] = new RStructure()
  protected implicit lazy val buzzer: Renderable[Buzzer] = new RBuzzer
  protected implicit lazy val service: Renderable[Service] = new RService()
  protected implicit lazy val typedef: Renderable[TypeDef] = new RTypeDef()
  protected implicit lazy val adtMember: Renderable[AdtMember] = new RAdtMember()
  protected implicit lazy val simpleStructure: Renderable[SimpleStructure] = new RSimpleStructure()
  protected implicit lazy val typedStream: Renderable[TypedStream] = new RTypedStream()
  protected implicit lazy val streams: Renderable[Streams] = new RStreams()
  protected implicit lazy val anno: Renderable[Anno] = new RAnno()
  protected implicit lazy val value: Renderable[ConstValue] = new RValue()

  protected implicit def typeid[T <: TypeId]: Renderable[T]
}
