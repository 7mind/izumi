package com.github.pshirshov.izumi.idealingua.model.runtime.model

import java.net.{URLDecoder, URLEncoder}

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.serialization.ILSchemaSerializerJson4sImpl
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId}

import scala.language.higherKinds

trait IDLGenerated extends Any
trait IDLGeneratedCompanion extends IDLGenerated

case class IDLTypeInfo(typeId: TypeId, domain: IDLDomainCompanion, signature: Int)

trait IDLGeneratedType extends Any with IDLGenerated {
  def _info: IDLTypeInfo
}

trait IDLIdentifier extends Any {
  this: IDLGeneratedType =>
}

object IDLIdentifier {
  // TODO: we may need to use a better escaping
  def escape(s: String): String = URLEncoder.encode(s, "UTF-8")
  def unescape(s: String): String = URLDecoder.decode(s, "UTF-8")
}

trait IDLService extends IDLGeneratedType {
  type InputType <: IDLInput
  type OutputType <: IDLOutput
  type Result[+R <: OutputType]

  def inputClass: Class[InputType]
  def outputClass: Class[OutputType]
}

trait IDLRpc extends Any with IDLGeneratedType
trait IDLInput extends Any with IDLRpc
trait IDLOutput extends Any with IDLRpc

trait IDLAdtElementCompanion extends IDLGeneratedCompanion
trait IDLServiceCompanion extends IDLGeneratedCompanion
trait IDLTypeCompanion extends IDLGeneratedCompanion

trait IDLDomainCompanion extends IDLGeneratedCompanion {
  def id: DomainId
  def types: Map[TypeId, Class[_]]
  def classes: Map[Class[_], TypeId]

  def schema: DomainDefinition = cachedSchema

  protected def serializedSchema: String

  private lazy val cachedSchema: DomainDefinition = schemaSerializer.parseSchema(serializedSchema)
  protected val schemaSerializer: ILSchemaSerializerJson4sImpl.type = ILSchemaSerializerJson4sImpl
}

trait IDLEnum extends IDLGenerated {
  type Element <: IDLEnumElement
  def all: Seq[Element]
  def parse(value: String): Element
}

trait IDLEnumElement extends IDLGeneratedType


trait IDLAdt extends IDLGenerated {
  type Element <: IDLAdtElement
}

trait IDLAdtElement extends IDLGeneratedType

