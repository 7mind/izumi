package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor

import StaticDSL._

trait StaticModuleDef extends ModuleDef {
  def stat[T: Tag: AnyConstructor]: Unit = {
    make[T].statically
  }
}
