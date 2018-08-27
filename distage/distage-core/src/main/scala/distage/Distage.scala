package distage

import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.language.higherKinds

trait Distage {

  type ModuleDef = model.definition.ModuleDef

  type Injector = model.Injector

  type Tag[T] = RuntimeDIUniverse.Tag[T]
  val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  type TagKK[T[_, _]] = RuntimeDIUniverse.TagKK[T]
  val TagKK: RuntimeDIUniverse.TagKK.type = RuntimeDIUniverse.TagKK

  type Planner = model.Planner
  type Locator = model.Locator
  type Producer = model.Producer

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]

  type DIKey = RuntimeDIUniverse.DIKey
  val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  type SafeType = RuntimeDIUniverse.SafeType
  val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  type ModuleBase = model.definition.ModuleBase

  type BootstrapModule = model.definition.BootstrapModule
  val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  type BootstrapModuleDef = model.definition.BootstrapModuleDef

}
