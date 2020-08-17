import izumi.distage.model.plan.ExecutableOp
import izumi.distage.planning.extensions
import izumi.distage.{constructors, model, planning}

package object distage extends Distage {

  override type ModuleDef = model.definition.ModuleDef

  override type Injector = model.Injector
  override type Planner = model.Planner
  override type Producer = model.Producer

  override type Activation = model.definition.Activation
  override val Activation: model.definition.Activation.type = model.definition.Activation

  override type Roots = model.plan.Roots
  override val Roots: model.plan.Roots.type = model.plan.Roots

  override type Locator = model.Locator
  override type LocatorRef = model.recursive.LocatorRef

  override type LocatorDef = model.definition.LocatorDef

  override type Id = model.definition.Id
  override type With[T] = model.definition.With[T]
  override type impl = model.definition.impl

  override type Tag[T] = izumi.reflect.Tag[T]
  override val Tag: izumi.reflect.Tag.type = izumi.reflect.Tag

  override type TagK[T[_]] = izumi.reflect.TagK[T]
  override val TagK: izumi.reflect.TagK.type = izumi.reflect.TagK

  override type DIResource[+F[_], Resource] = model.definition.DIResource[F, Resource]
  override val DIResource: model.definition.DIResource.type = model.definition.DIResource

  override type DIResourceBase[+F[_], +Resource] = model.definition.DIResource.DIResourceBase[F, Resource]

  override type Axis = model.definition.Axis
  override val Axis: model.definition.Axis.type = model.definition.Axis

  override val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis

  override type DIKey = model.reflection.DIKey
  override val DIKey: model.reflection.DIKey.type = model.reflection.DIKey

  override type Functoid[+A] = model.providers.Functoid[A]
  override val Functoid: model.providers.Functoid.type = model.providers.Functoid

  override type ClassConstructor[T] = constructors.ClassConstructor[T]
  override val ClassConstructor: constructors.ClassConstructor.type = constructors.ClassConstructor

  override type TraitConstructor[T] = constructors.TraitConstructor[T]
  override val TraitConstructor: constructors.TraitConstructor.type = constructors.TraitConstructor

  override type FactoryConstructor[T] = constructors.FactoryConstructor[T]
  override val FactoryConstructor: constructors.FactoryConstructor.type = constructors.FactoryConstructor

  override type HasConstructor[T] = constructors.HasConstructor[T]
  override val HasConstructor: constructors.HasConstructor.type = constructors.HasConstructor

  @deprecated("Use `distage.Functoid` instead of ProviderMagnet", "0.11.0")
  override type ProviderMagnet[+A] = Functoid[A]
  @deprecated("Use `distage.Functoid` instead of ProviderMagnet", "0.11.0")
  override val ProviderMagnet: model.providers.Functoid.type = model.providers.Functoid

  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 0.11.1")
  override type GCMode = model.plan.Roots
  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 0.11.1")
  override val GCMode: model.plan.Roots.type = model.plan.Roots

  override type BindingTag = model.definition.BindingTag
  override val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  override type PlannerInput = model.PlannerInput
  override val PlannerInput: model.PlannerInput.type = model.PlannerInput

  override type GraphDumpObserver = extensions.GraphDumpObserver
  override val GraphDumpObserver: extensions.GraphDumpObserver.type = extensions.GraphDumpObserver

  override type GraphDumpBootstrapModule = extensions.GraphDumpBootstrapModule
  override val GraphDumpBootstrapModule: extensions.GraphDumpBootstrapModule.type = extensions.GraphDumpBootstrapModule

  override type OrderedPlan = model.plan.OrderedPlan
  override val OrderedPlan: model.plan.OrderedPlan.type = model.plan.OrderedPlan
  override type SemiPlan = model.plan.SemiPlan
  override val SemiPlan: model.plan.SemiPlan.type = model.plan.SemiPlan
  override type AbstractPlan[OpType <: ExecutableOp] = model.plan.AbstractPlan[OpType]

  override type SafeType = model.reflection.SafeType
  override val SafeType: model.reflection.SafeType.type = model.reflection.SafeType

  override type ModuleBase = model.definition.ModuleBase
  override val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  override type Module = model.definition.Module
  override val Module: model.definition.Module.type = model.definition.Module

  override type BootstrapModule = model.definition.BootstrapModule
  override val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  override type BootstrapModuleDef = model.definition.BootstrapModuleDef

  override type AutoSetModule = planning.AutoSetModule
  override val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  override type TagKK[T[_, _]] = izumi.reflect.TagKK[T]
  override val TagKK: izumi.reflect.TagKK.type = izumi.reflect.TagKK

  override type TagK3[T[_, _, _]] = izumi.reflect.TagK3[T]
  override val TagK3: izumi.reflect.TagK3.type = izumi.reflect.TagK3

  override type TagT[T[_[_]]] = izumi.reflect.TagT[T]
  override val TagT: izumi.reflect.TagT.type = izumi.reflect.TagT

  override type TagTK[T[_[_], _]] = izumi.reflect.TagTK[T]
  override val TagTK: izumi.reflect.TagTK.type = izumi.reflect.TagTK

  override type TagTKK[T[_[_], _, _]] = izumi.reflect.TagTKK[T]
  override val TagTKK: izumi.reflect.TagTKK.type = izumi.reflect.TagTKK

  override type TagTK3[T[_[_], _, _, _]] = izumi.reflect.TagTK3[T]
  override val TagTK3: izumi.reflect.TagTK3.type = izumi.reflect.TagTK3

}
