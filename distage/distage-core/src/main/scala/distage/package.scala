import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.{model, planning}
import izumi.fundamentals.reflection.Tags

package object distage extends Distage {

  override type ModuleDef = model.definition.ModuleDef

  override type Activation = model.definition.Activation
  override val Activation: model.definition.Activation.type = model.definition.Activation

  override type Injector = model.Injector
  override type Planner = model.Planner
  override type Producer = model.Producer

  override type Locator = model.Locator
  override type LocatorRef = model.Locator.LocatorRef

  override type Tag[T] = Tags.Tag[T]
  override val Tag: Tags.Tag.type = Tags.Tag

  override type TagK[T[_]] = Tags.TagK[T]
  override val TagK: Tags.TagK.type = Tags.TagK

  override type DIResource[+F[_], Resource] = model.definition.DIResource[F, Resource]
  override val DIResource: model.definition.DIResource.type = model.definition.DIResource

  override type DIResourceBase[+F[_], +Resource] = model.definition.DIResource.DIResourceBase[F, Resource]

  override type ProviderMagnet[+A] = model.providers.ProviderMagnet[A]
  override val ProviderMagnet: model.providers.ProviderMagnet.type = model.providers.ProviderMagnet


  override type GCMode = model.plan.GCMode
  override val GCMode: model.plan.GCMode.type = model.plan.GCMode

  override val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis

  override type Axis = model.definition.Axis
  override val Axis: model.definition.Axis.type = model.definition.Axis

  override type BindingTag = model.definition.BindingTag
  override val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  override type PlannerInput = model.PlannerInput
  override val PlannerInput: model.PlannerInput.type = model.PlannerInput

//  override type GraphDumpObserver = extensions.GraphDumpObserver
//  override val GraphDumpObserver: extensions.GraphDumpObserver.type = extensions.GraphDumpObserver
//
//  override type GraphDumpBootstrapModule = extensions.GraphDumpBootstrapModule
//  override val GraphDumpBootstrapModule: extensions.GraphDumpBootstrapModule.type = extensions.GraphDumpBootstrapModule

  override type OrderedPlan = model.plan.OrderedPlan
  override val OrderedPlan: model.plan.OrderedPlan.type = model.plan.OrderedPlan
  override type SemiPlan = model.plan.SemiPlan
  override val SemiPlan: model.plan.SemiPlan.type = model.plan.SemiPlan
  override type AbstractPlan[OpType <: ExecutableOp] = model.plan.AbstractPlan[OpType]

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

  override type BootstrapModuleDef = model.definition.BootstrapModuleDef

  override type AutoSetModule = planning.AutoSetModule
  override val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  override type TagKK[T[_, _]] = Tags.TagKK[T]
  override val TagKK: Tags.TagKK.type = Tags.TagKK

  override type TagK3[T[_, _, _]] = Tags.TagK3[T]
  override val TagK3: Tags.TagK3.type = Tags.TagK3

  override type TagT[T[_[_]]] = Tags.TagT[T]
  override val TagT: Tags.TagT.type = Tags.TagT

  override type TagTK[T[_[_], _]] = Tags.TagTK[T]
  override val TagTK: Tags.TagTK.type = Tags.TagTK

  override type TagTKK[T[_[_], _, _]] = Tags.TagTKK[T]
  override val TagTKK: Tags.TagTKK.type = Tags.TagTKK

  override type TagTK3[T[_[_], _, _, _]] = Tags.TagTK3[T]
  override val TagTK3: Tags.TagTK3.type = Tags.TagTK3
}
