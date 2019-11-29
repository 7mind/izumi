package distage

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.{model, planning}

trait Distage {

  type ModuleDef = model.definition.ModuleDef
  type Injector = model.Injector

  type Tag[T] = RuntimeDIUniverse.Tag[T]
  val Tag: RuntimeDIUniverse.Tag.type = RuntimeDIUniverse.Tag

  type TagK[T[_]] = RuntimeDIUniverse.TagK[T]
  val TagK: RuntimeDIUniverse.TagK.type = RuntimeDIUniverse.TagK

  type DIResource[+F[_], Resource] = model.definition.DIResource[F, Resource]
  val DIResource: model.definition.DIResource.type = model.definition.DIResource

  type DIResourceBase[+F[_], +Resource] = model.definition.DIResource.DIResourceBase[F, Resource]

  type Planner = model.Planner
  type Locator = model.Locator
  type Producer = model.Producer

  type GCMode = model.plan.GCMode
  val GCMode: model.plan.GCMode.type = model.plan.GCMode

  val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis

  type Axis[+MM <: AxisValue] = model.definition.Axis[MM]
  val Axis: model.definition.Axis.type = model.definition.Axis

  type BindingTag = model.definition.BindingTag
  val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  type PlannerInput = model.PlannerInput
  val PlannerInput: model.PlannerInput.type = model.PlannerInput

  type OrderedPlan = model.plan.OrderedPlan
  val OrderedPlan: model.plan.OrderedPlan.type = model.plan.OrderedPlan
  type SemiPlan = model.plan.SemiPlan
  val SemiPlan: model.plan.SemiPlan.type = model.plan.SemiPlan
  type AbstractPlan[OpType <: ExecutableOp] = model.plan.AbstractPlan[OpType]

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]

  type DIKey = RuntimeDIUniverse.DIKey
  val DIKey: RuntimeDIUniverse.DIKey.type = RuntimeDIUniverse.DIKey

  type SafeType = RuntimeDIUniverse.SafeType
  val SafeType: RuntimeDIUniverse.SafeType.type = RuntimeDIUniverse.SafeType

  type ModuleBase = model.definition.ModuleBase
  val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  type Module = model.definition.Module
  val Module: model.definition.Module.type = model.definition.Module

  type BootstrapModule = model.definition.BootstrapModule
  val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  val CompactPlanFormatter: model.plan.repr.CompactPlanFormatter.type = model.plan.repr.CompactPlanFormatter

  type BootstrapModuleDef = model.definition.BootstrapModuleDef

  type AutoSetModule = planning.AutoSetModule
  val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  type TagKK[T[_, _]] = RuntimeDIUniverse.TagKK[T]
  val TagKK: RuntimeDIUniverse.TagKK.type = RuntimeDIUniverse.TagKK

  type TagK3[T[_, _, _]] = RuntimeDIUniverse.TagK3[T]
  val TagK3: RuntimeDIUniverse.TagK3.type = RuntimeDIUniverse.TagK3

  type TagT[T[_[_]]] = RuntimeDIUniverse.TagT[T]
  val TagT: RuntimeDIUniverse.TagT.type = RuntimeDIUniverse.TagT

  type TagTK[T[_[_], _]] = RuntimeDIUniverse.TagTK[T]
  val TagTK: RuntimeDIUniverse.TagTK.type = RuntimeDIUniverse.TagTK

  type TagTKK[T[_[_], _, _]] = RuntimeDIUniverse.TagTKK[T]
  val TagTKK: RuntimeDIUniverse.TagTKK.type = RuntimeDIUniverse.TagTKK

  type TagTK3[T[_[_], _, _, _]] = RuntimeDIUniverse.TagTK3[T]
  val TagTK3: RuntimeDIUniverse.TagTK3.type = RuntimeDIUniverse.TagTK3

}
