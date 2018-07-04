package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Alias
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._




class TypespaceImpl(val domain: DomainDefinition) extends Typespace with TypeResolver {
  lazy val types: TypeCollection = new TypeCollection(domain)
  protected[typespace] lazy val referenced: Map[DomainId, Typespace] = domain.referenced.mapValues(d => new TypespaceImpl(d))
  private lazy val index: CMap[TypeId, TypeDef] = types.index


  override lazy val tools: TypespaceTools = new TypespaceToolsImpl(types)

  override lazy val inheritance: InheritanceQueries = new InheritanceQueriesImpl(this, types)

  override lazy val structure: StructuralQueries = new StructuralQueriesImpl(types, this, inheritance, tools)

  def toDtoName(id: TypeId): String = types.toDtoName(id)


  override def implId(id: InterfaceId): DTOId = tools.implId(id)

  override def sourceId(id: DTOId): Option[InterfaceId] = tools.sourceId(id)

  override def defnId(id: StructureId): InterfaceId = tools.defnId(id)

  def apply(id: ServiceId): Service = {
    types.services(id)
  }

  def dealias(t: TypeId): TypeId = {
    t match {
      case a: AliasId =>
        dealias(apply(a).asInstanceOf[Alias].target)

      case o =>
        o
    }
  }

  def apply(id: TypeId): TypeDef = {
    if (index.underlying.contains(id)) {
      index.fetch(id)
    } else {
      referenced(id.path.domain).apply(id)
    }
  }

}



