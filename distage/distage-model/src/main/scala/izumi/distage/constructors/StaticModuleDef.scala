package izumi.distage.constructors

import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.reflection.Tags.Tag
import izumi.fundamentals.platform.language.CodePositionMaterializer

// TODO: improve
@deprecated("will be replaced", "0.10")
trait StaticModuleDef extends ModuleDef with StaticDSL {

  final def stat[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): Unit = {
    make[T]
  }

}
