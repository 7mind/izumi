package com.github.pshirshov.izumi.idealingua.model.runtime.model

import java.net.{URLDecoder, URLEncoder}

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.serialization.ILSchemaSerializerJson4sImpl
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId}

import scala.language.higherKinds

trait IDLGenerated extends Any

trait IDLGeneratedCompanion extends IDLGenerated

trait IDLGeneratedType extends Any with IDLGenerated


trait IDLIdentifier extends Any {
  this: IDLGeneratedType =>
}

object IDLIdentifier {
  // TODO: we may need to use a better escaping
  def escape(s: String): String = URLEncoder.encode(s, "UTF-8")

  def unescape(s: String): String = URLDecoder.decode(s, "UTF-8")
}


trait IDLRpc extends Any with IDLGeneratedType

trait IDLInput extends Any with IDLRpc

trait IDLOutput extends Any with IDLRpc

trait IDLAdtElementCompanion extends IDLGeneratedCompanion

trait IDLServiceCompanion extends IDLGeneratedCompanion

trait IDLTypeCompanion extends IDLGeneratedCompanion


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

