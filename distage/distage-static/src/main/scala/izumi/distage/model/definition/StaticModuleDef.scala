package izumi.distage.model.definition

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import izumi.distage.provisioning.AnyConstructor
import izumi.fundamentals.reflection.CodePositionMaterializer

// TODO: improve
trait StaticModuleDef extends ModuleDef with StaticDSL {

  final def stat[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): Unit = {
    super.make[T](Tag[T], pos).stat[T]
  }

}
