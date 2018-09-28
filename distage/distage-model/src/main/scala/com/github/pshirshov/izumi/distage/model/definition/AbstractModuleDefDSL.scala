package com.github.pshirshov.izumi.distage.model.definition

import scala.collection.mutable

trait AbstractModuleDefDSL {


}

object AbstractModuleDefDSL {
  sealed trait BindingRef

  final case class SingletonRef(var ref: Binding) extends BindingRef

  final case class SetRef(emptySetBinding: SingletonRef, all: mutable.ArrayBuffer[SingletonRef]) extends BindingRef
}
