import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.{model, planning}

package object distage extends Distage {

  override type ModuleDef = model.definition.ModuleDef
  override type Injector = model.Injector

  override type Tag[T] = RuntimeDIUniverse.Tag[T]
  override val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  override type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  override val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  override type DIResource[+F[_], Resource] = model.definition.DIResource[F, Resource]
  override val DIResource: model.definition.DIResource.type = model.definition.DIResource

  override type Planner = model.Planner
  override type Locator = model.Locator
  override type Producer = model.Producer

  override type PlannerInput = model.PlannerInput
  override val PlannerInput: model.PlannerInput.type = model.PlannerInput

  override type OrderedPlan = model.plan.OrderedPlan
  override val OrderedPlan: model.plan.OrderedPlan.type = model.plan.OrderedPlan
  override type SemiPlan = model.plan.SemiPlan
  override val SemiPlan: model.plan.SemiPlan.type = model.plan.SemiPlan
  override type AbstractPlan = model.plan.AbstractPlan

  override type Id = model.definition.Id
  override type With[T] = model.definition.With[T]

  override type DIKey = RuntimeDIUniverse.DIKey
  override val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  override type SafeType = RuntimeDIUniverse.SafeType
  override val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  override type ModuleBase = model.definition.ModuleBase
  override val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  override type Module = model.definition.Module
  override val Module: model.definition.Module.type = model.definition.Module

  override type BootstrapModule = model.definition.BootstrapModule
  override val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  override val CompactPlanFormatter: model.plan.CompactPlanFormatter.type = model.plan.CompactPlanFormatter

  override type BootstrapModuleDef = model.definition.BootstrapModuleDef

  override type AutoSetModule = planning.AutoSetModule
  override val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  override type TagKK[T[_, _]] = RuntimeDIUniverse.TagKK[T]
  override val TagKK: RuntimeDIUniverse.TagKK.type = RuntimeDIUniverse.TagKK

  override type TagK3[T[_, _, _]] = RuntimeDIUniverse.TagK3[T]
  override val TagK3: RuntimeDIUniverse.TagK3.type = RuntimeDIUniverse.TagK3

  override type TagT[T[_[_]]] = RuntimeDIUniverse.TagT[T]
  override val TagT: RuntimeDIUniverse.TagT.type = RuntimeDIUniverse.TagT

  override type TagTK[T[_[_], _]] = RuntimeDIUniverse.TagTK[T]
  override val TagTK: RuntimeDIUniverse.TagTK.type = RuntimeDIUniverse.TagTK

  override type TagTKK[T[_[_], _, _]] = RuntimeDIUniverse.TagTKK[T]
  override val TagTKK: RuntimeDIUniverse.TagTKK.type = RuntimeDIUniverse.TagTKK

  override type TagTK3[T[_[_], _, _, _]] = RuntimeDIUniverse.TagTK3[T]
  override val TagTK3: RuntimeDIUniverse.TagTK3.type = RuntimeDIUniverse.TagTK3
}
