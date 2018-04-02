//package com.github.pshirshov.izumi.idealingua.tools.serialization
//
//import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainDefinition
//
//object ILSchemaSerializerJson4sImpl extends ILSchemaSerializer {
//  import org.json4s._
//  import org.json4s.native.Serialization.{read, write}
//  implicit val formats: Formats  = DefaultFormats
//
//  def serializeSchema(domain: DomainDefinition): String = {
//    write(domain.copy(referenced = Map.empty))
//  }
//
//  override def parseSchema(s: String): DomainDefinition = {
//    read(s)
//  }
//}
