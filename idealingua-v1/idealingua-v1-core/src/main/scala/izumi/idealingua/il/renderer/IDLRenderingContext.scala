package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.common._
import izumi.idealingua.model.il.ast.typed.{TypeDef, _}

class IDLRenderingContext(val domain: DomainDefinition, val options: IDLRenderingOptions) {
  implicit lazy val domainDefinition: Renderable[DomainDefinition] = new RDomain(this)
  implicit lazy val streamDirection: Renderable[StreamDirection] = RStreamDirection
  implicit lazy val domainId: Renderable[DomainId] = RDomainId
  implicit lazy val idField: Renderable[IdField] = new RIdField(this)
  implicit lazy val field: Renderable[Field] = new RField(this)
  implicit lazy val structure: Renderable[Structure] = new RStructure(this)
  implicit lazy val buzzer: Renderable[Buzzer] = new RBuzzer(this)
  implicit lazy val service: Renderable[Service] = new RService(this)
  implicit lazy val typedef: Renderable[TypeDef] = new RTypeDef(this)
  implicit lazy val adtMember: Renderable[AdtMember] = new RAdtMember(this)
  implicit lazy val simpleStructure: Renderable[SimpleStructure] = new RSimpleStructure(this)
  implicit lazy val typedStream: Renderable[TypedStream] = new RTypedStream(this)
  implicit lazy val streams: Renderable[Streams] = new RStreams(this)
  implicit lazy val anno: Renderable[Anno] = new RAnno(this)
  implicit lazy val valueRenderer: Renderable[ConstValue] = new RValue(this)

  implicit val meta: MetaRenderer = new MetaRenderer(this)
  implicit val methods: MethodRenderer = new MethodRenderer(this)

  implicit def typeid[T <: TypeId]: Renderable[T] = (value: T) => new RTypeId(IDLRenderingContext.this).render(value)

}


