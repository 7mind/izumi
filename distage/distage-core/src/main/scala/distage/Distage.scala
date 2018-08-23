package distage

import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.language.higherKinds

trait Distage {

  type Tag[T] = RuntimeDIUniverse.Tag[T]
  val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  type TagKK[T[_, _]] = RuntimeDIUniverse.TagKK[T]
  val TagKK: RuntimeDIUniverse.TagKK.type = RuntimeDIUniverse.TagKK

  type DIKey = RuntimeDIUniverse.DIKey
  val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  type SafeType = RuntimeDIUniverse.SafeType
  val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  type Injector = model.Injector

  type ModuleBase = model.definition.ModuleBase

  type BootstrapModule = model.definition.BootstrapModule
  val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  type BootstrapModuleDef = model.definition.BootstrapModuleDef
  val BootstrapModuleDef: model.definition.BootstrapModuleDef.type = model.definition.BootstrapModuleDef

  type ModuleDef = model.definition.ModuleDef

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]

  type Planner = model.Planner
  type Locator = model.Locator
  type Producer = model.Producer
}
