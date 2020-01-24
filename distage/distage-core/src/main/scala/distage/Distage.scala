package distage

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.planning.extensions
import izumi.distage.{constructors, model, planning}
import izumi.fundamentals.reflection.Tags

trait Distage {

  type ModuleDef = model.definition.ModuleDef

  type Activation = model.definition.Activation
  val Activation: model.definition.Activation.type = model.definition.Activation

  type Injector = model.Injector
  type Planner = model.Planner
  type Producer = model.Producer

  type Locator = model.Locator
  type LocatorRef = model.reflective.LocatorRef

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]
  type impl = model.definition.impl

  type Tag[T] = Tags.Tag[T]
  val Tag: Tags.Tag.type = Tags.Tag

  type TagK[T[_]] = Tags.TagK[T]
  val TagK: Tags.TagK.type = Tags.TagK

  type DIResource[+F[_], Resource] = model.definition.DIResource[F, Resource]
  val DIResource: model.definition.DIResource.type = model.definition.DIResource

  type DIResourceBase[+F[_], +Resource] = model.definition.DIResource.DIResourceBase[F, Resource]

  type Axis = model.definition.Axis
  val Axis: model.definition.Axis.type = model.definition.Axis

  val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis

  type ProviderMagnet[+A] = model.providers.ProviderMagnet[A]
  val ProviderMagnet: model.providers.ProviderMagnet.type = model.providers.ProviderMagnet

  type ClassConstructor[T] = constructors.ClassConstructor[T]
  val ClassConstructor: constructors.ClassConstructor.type = constructors.ClassConstructor

  type TraitConstructor[T] = constructors.TraitConstructor[T]
  val TraitConstructor: constructors.TraitConstructor.type = constructors.TraitConstructor

  type FactoryConstructor[T] = constructors.FactoryConstructor[T]
  val FactoryConstructor: constructors.FactoryConstructor.type = constructors.FactoryConstructor

  type GCMode = model.plan.GCMode
  val GCMode: model.plan.GCMode.type = model.plan.GCMode

  type BindingTag = model.definition.BindingTag
  val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  type PlannerInput = model.PlannerInput
  val PlannerInput: model.PlannerInput.type = model.PlannerInput

  type GraphDumpObserver = extensions.GraphDumpObserver
  val GraphDumpObserver: extensions.GraphDumpObserver.type = extensions.GraphDumpObserver

  type GraphDumpBootstrapModule = extensions.GraphDumpBootstrapModule
  val GraphDumpBootstrapModule: extensions.GraphDumpBootstrapModule.type = extensions.GraphDumpBootstrapModule

  type OrderedPlan = model.plan.OrderedPlan
  val OrderedPlan: model.plan.OrderedPlan.type = model.plan.OrderedPlan
  type SemiPlan = model.plan.SemiPlan
  val SemiPlan: model.plan.SemiPlan.type = model.plan.SemiPlan
  type AbstractPlan[OpType <: ExecutableOp] = model.plan.AbstractPlan[OpType]

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

  type BootstrapModuleDef = model.definition.BootstrapModuleDef

  type AutoSetModule = planning.AutoSetModule
  val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  type TagKK[T[_, _]] = Tags.TagKK[T]
  val TagKK: Tags.TagKK.type = Tags.TagKK

  type TagK3[T[_, _, _]] = Tags.TagK3[T]
  val TagK3: Tags.TagK3.type = Tags.TagK3

  type TagT[T[_[_]]] = Tags.TagT[T]
  val TagT: Tags.TagT.type = Tags.TagT

  type TagTK[T[_[_], _]] = Tags.TagTK[T]
  val TagTK: Tags.TagTK.type = Tags.TagTK

  type TagTKK[T[_[_], _, _]] = Tags.TagTKK[T]
  val TagTKK: Tags.TagTKK.type = Tags.TagTKK

  type TagTK3[T[_[_], _, _, _]] = Tags.TagTK3[T]
  val TagTK3: Tags.TagTK3.type = Tags.TagTK3

}
