package distage

import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.definition
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.language.higherKinds

trait Distage {

  type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  type Tag[T] = RuntimeDIUniverse.Tag[T]
  val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  type DIKey = RuntimeDIUniverse.DIKey
  val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  type SafeType = RuntimeDIUniverse.SafeType
  val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  type Injector = model.Injector

  type ModuleBase = model.definition.ModuleBase
  type ModuleDef = model.definition.ModuleDef

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]

  type Planner = model.Planner
  type Locator = model.Locator
  type Producer = model.Producer

  type SimpleModuleDef = definition.SimpleModuleDef
  val SimpleModuleDef: definition.SimpleModuleDef.type = definition.SimpleModuleDef

}
