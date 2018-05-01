package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set.catsKernelStdPartialOrderForSet
import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import com.github.pshirshov.izumi.distage.model.definition.{BindingDSL, ModuleDef, TrivialModuleDef}

object DIStageInstances {
  implicit val catsKernelStdPartialOrderForModuleDef: PartialOrder[ModuleDef] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForModuleDef: BoundedSemilattice[ModuleDef] =
    new ModuleDefSemilattice

  implicit def catsKernelStdSemilatticeForBindingDSL: BoundedSemilattice[BindingDSL] =
    new BindingDSLSemilattice

  implicit val catsKernelStdHashForModuleDef: Hash[ModuleDef] =
    Hash.fromUniversalHashCode[ModuleDef]
}

class ModuleDefSemilattice extends BoundedSemilattice[ModuleDef] {
  def empty: ModuleDef = TrivialModuleDef
  def combine(x: ModuleDef, y: ModuleDef): ModuleDef = x ++ y
}

class BindingDSLSemilattice extends BoundedSemilattice[BindingDSL] {
  def empty: BindingDSL = TrivialModuleDef
  def combine(x: BindingDSL, y: BindingDSL): BindingDSL = x ++ y
}
