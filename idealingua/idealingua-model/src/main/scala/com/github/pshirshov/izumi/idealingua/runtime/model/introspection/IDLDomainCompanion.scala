package com.github.pshirshov.izumi.idealingua.runtime.model.introspection

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DomainDefinition, DomainId}
import com.github.pshirshov.izumi.idealingua.tools.serialization.ILSchemaSerializer

trait IDLDomainCompanion {
  def id: DomainId

  def types: Map[TypeId, Class[_]]

  def classes: Map[Class[_], TypeId]

  def schema: DomainDefinition = cachedSchema

  protected def serializedSchema: String

  protected def referencedDomains: Map[DomainId, DomainDefinition]

  private lazy val cachedSchema: DomainDefinition = schemaSerializer.parseSchema(serializedSchema).copy(referenced = referencedDomains)
  protected val schemaSerializer: ILSchemaSerializer
}
