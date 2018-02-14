package com.github.pshirshov.izumi.idealingua.model.runtime
import com.github.pshirshov.izumi.idealingua.model.finaldef._


import scala.reflect._

trait IDLGenerated extends Any {
  def companion: IDLTypeCompanion // TODO: it would be great to remove it
}

trait IDLService {
  // TODO: it would be great to remove it
  def companion: IDLServiceCompanion
}


trait IDLIdentifier extends Any {
  this: IDLGenerated =>
}

object IDLIdentifier {
  // TODO: here we should escape colons
  def escape(s: String): String = s

  def unescape(s: String): String = s
}


trait IDLRpc {}


trait IDLInput extends IDLRpc {

}

trait IDLOutput extends IDLRpc {

}

trait IDLServiceCompanion {
  type InputType <: IDLInput
  type OutputType <: IDLOutput

  def inputTag: ClassTag[InputType]
  def outputTag: ClassTag[OutputType]
  
  def schema: Service
  def domain: IDLDomainCompanion
}

trait IDLDomainCompanion {
  def domain: DomainDefinition
}

trait IDLEnumElement {}

trait IDLEnum {
  type Element <: IDLEnumElement
  def all: Seq[Element]
}

trait IDLTypeCompanion {
  def definition: FinalDefinition
  def domain: IDLDomainCompanion
}
