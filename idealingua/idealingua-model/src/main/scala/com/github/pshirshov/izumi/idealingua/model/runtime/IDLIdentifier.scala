package com.github.pshirshov.izumi.idealingua.model.runtime
import java.net.{URLDecoder, URLEncoder}

import scala.reflect._

trait IDLGenerated extends Any
trait IDLGeneratedCompanion extends IDLGenerated

trait IDLGeneratedType extends Any with IDLGenerated {
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

  def inputTag: ClassTag[InputType]
  def outputTag: ClassTag[OutputType]
}

trait IDLRpc extends IDLGeneratedType
trait IDLInput extends IDLRpc
trait IDLOutput extends IDLRpc

trait IDLServiceCompanion extends IDLGeneratedCompanion
trait IDLTypeCompanion extends IDLGeneratedCompanion
trait IDLDomainCompanion extends IDLGeneratedCompanion

trait IDLEnum extends IDLGeneratedType {
  type Element <: IDLEnumElement
  def all: Seq[Element]
}

trait IDLEnumElement extends IDLGeneratedType


