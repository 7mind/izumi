package com.github.pshirshov.izumi.idealingua.runtime.model

trait IDLGenerated extends Any

trait IDLGeneratedCompanion extends IDLGenerated

trait IDLGeneratedType extends Any with IDLGenerated

trait IDLRpc extends Any with IDLGeneratedType

trait IDLInput extends Any with IDLRpc

trait IDLOutput extends Any with IDLRpc

trait IDLAdtElementCompanion extends IDLGeneratedCompanion

trait IDLServiceCompanion extends IDLGeneratedCompanion

trait IDLTypeCompanion extends IDLGeneratedCompanion

trait IDLEnumElement extends IDLGeneratedType

trait IDLAdtElement extends IDLGeneratedType
