package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

// TODO: improve
trait StaticModuleDef extends ModuleDef with StaticDSL {

  final def stat[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): Unit = {
    super.make[T](Tag[T], pos).stat[T]
  }

}
