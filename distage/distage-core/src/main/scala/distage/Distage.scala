package distage

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.planning.extensions
import izumi.distage.{constructors, model, planning}

trait Distage {

  type ModuleDef = model.definition.ModuleDef

  type Activation = model.definition.Activation
  val Activation: model.definition.Activation.type = model.definition.Activation

  type Injector = model.Injector
  type Planner = model.Planner
  type Producer = model.Producer

  type Locator = model.Locator
  type LocatorRef = model.recursive.LocatorRef

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]
  type impl = model.definition.impl

  type Tag[T] = izumi.reflect.Tag[T]
  val Tag: izumi.reflect.Tag.type = izumi.reflect.Tag

  type TagK[T[_]] = izumi.reflect.TagK[T]
  val TagK: izumi.reflect.TagK.type = izumi.reflect.TagK

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

  type HasConstructor[T] = constructors.HasConstructor[T]
  val HasConstructor: constructors.HasConstructor.type = constructors.HasConstructor

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

  type DIKey = model.reflection.DIKey
  val DIKey: model.reflection.DIKey.type = model.reflection.DIKey

  type SafeType = model.reflection.SafeType
  val SafeType: model.reflection.SafeType.type = model.reflection.SafeType

  type ModuleBase = model.definition.ModuleBase
  val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  type Module = model.definition.Module
  val Module: model.definition.Module.type = model.definition.Module

  type BootstrapModule = model.definition.BootstrapModule
  val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  type BootstrapModuleDef = model.definition.BootstrapModuleDef

  type AutoSetModule = planning.AutoSetModule
  val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  type TagKK[T[_, _]] = izumi.reflect.TagKK[T]
  val TagKK: izumi.reflect.TagKK.type = izumi.reflect.TagKK

  type TagK3[T[_, _, _]] = izumi.reflect.TagK3[T]
  val TagK3: izumi.reflect.TagK3.type = izumi.reflect.TagK3

  type TagT[T[_[_]]] = izumi.reflect.TagT[T]
  val TagT: izumi.reflect.TagT.type = izumi.reflect.TagT

  type TagTK[T[_[_], _]] = izumi.reflect.TagTK[T]
  val TagTK: izumi.reflect.TagTK.type = izumi.reflect.TagTK

  type TagTKK[T[_[_], _, _]] = izumi.reflect.TagTKK[T]
  val TagTKK: izumi.reflect.TagTKK.type = izumi.reflect.TagTKK

  type TagTK3[T[_[_], _, _, _]] = izumi.reflect.TagTK3[T]
  val TagTK3: izumi.reflect.TagTK3.type = izumi.reflect.TagTK3

}
