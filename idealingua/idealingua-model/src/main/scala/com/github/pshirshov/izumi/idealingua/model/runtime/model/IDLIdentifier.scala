package com.github.pshirshov.izumi.idealingua.model.runtime.model

import java.net.{URLDecoder, URLEncoder}

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.DomainId

import scala.language.higherKinds
import scala.reflect._

trait IDLGenerated extends Any
trait IDLGeneratedCompanion extends IDLGenerated

case class IDLTypeInfo(typeId: TypeId, domain: IDLDomainCompanion)

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

  def inputTag: ClassTag[InputType]
  def outputTag: ClassTag[OutputType]
}

trait IDLRpc extends Any with IDLGeneratedType
trait IDLInput extends Any with IDLRpc
trait IDLOutput extends Any with IDLRpc

trait IDLServiceCompanion extends IDLGeneratedCompanion
trait IDLTypeCompanion extends IDLGeneratedCompanion

trait IDLDomainCompanion extends IDLGeneratedCompanion {
  def id: DomainId
  def types: Map[TypeId, Class[_]]
  def classes: Map[Class[_], TypeId]
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

