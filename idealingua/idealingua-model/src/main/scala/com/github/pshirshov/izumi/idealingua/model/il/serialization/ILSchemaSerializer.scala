package com.github.pshirshov.izumi.idealingua.model.il.serialization

import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition

trait ILSchemaSerializer {
  def serializeSchema(domain: DomainDefinition): String
  def parseSchema(s: String): DomainDefinition

}


object ILSchemaSerializerJson4sImpl extends ILSchemaSerializer {
  import org.json4s._
  import org.json4s.native.Serialization.{write, read}
  implicit val formats: Formats  = DefaultFormats

  def serializeSchema(domain: DomainDefinition): String = {
    write(domain.copy(referenced = Map.empty))
  }

  override def parseSchema(s: String): DomainDefinition = {
    read(s)
  }
}
