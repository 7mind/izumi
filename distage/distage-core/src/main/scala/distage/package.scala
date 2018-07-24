import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.language.higherKinds

package object distage {

  type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  type Tag[T] = RuntimeDIUniverse.Tag[T]
  val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  type DIKey = RuntimeDIUniverse.DIKey
  val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  type SafeType = RuntimeDIUniverse.SafeType
  val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  type Injector = com.github.pshirshov.izumi.distage.model.Injector

  type ModuleBase = com.github.pshirshov.izumi.distage.model.definition.ModuleBase
  type ModuleDef = com.github.pshirshov.izumi.distage.model.definition.ModuleDef

  type Id = com.github.pshirshov.izumi.distage.model.definition.Id
  type With[T] = com.github.pshirshov.izumi.distage.model.definition.With[T]

  type Planner = com.github.pshirshov.izumi.distage.model.Planner
  type Locator = com.github.pshirshov.izumi.distage.model.Locator
  type Producer = com.github.pshirshov.izumi.distage.model.Producer

  type SimpleModuleDef = com.github.pshirshov.izumi.distage.model.definition.SimpleModuleDef
  val SimpleModuleDef: com.github.pshirshov.izumi.distage.model.definition.SimpleModuleDef.type = com.github.pshirshov.izumi.distage.model.definition.SimpleModuleDef

}
