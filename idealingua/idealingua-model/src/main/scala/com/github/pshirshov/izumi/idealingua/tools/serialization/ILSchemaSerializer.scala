package com.github.pshirshov.izumi.idealingua.tools.serialization

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainDefinition

trait ILSchemaSerializer {
  def serializeSchema(domain: DomainDefinition): String
  def parseSchema(s: String): DomainDefinition

}



