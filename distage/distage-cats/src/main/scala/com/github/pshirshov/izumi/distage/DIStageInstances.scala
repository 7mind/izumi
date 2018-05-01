package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set.catsKernelStdPartialOrderForSet
import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import com.github.pshirshov.izumi.distage.model.definition.{AbstractModuleDef, BindingDSL, TrivialModuleDef}

object DIStageInstances {
  implicit val catsKernelStdPartialOrderForModuleDef: PartialOrder[AbstractModuleDef] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForModuleDef: BoundedSemilattice[AbstractModuleDef] =
    new ModuleDefSemilattice

  implicit def catsKernelStdSemilatticeForBindingDSL: BoundedSemilattice[BindingDSL] =
    new BindingDSLSemilattice

  implicit val catsKernelStdHashForModuleDef: Hash[AbstractModuleDef] =
    Hash.fromUniversalHashCode

  class ModuleDefSemilattice extends BoundedSemilattice[AbstractModuleDef] {
    def empty: AbstractModuleDef = TrivialModuleDef(Set.empty)
    def combine(x: AbstractModuleDef, y: AbstractModuleDef): AbstractModuleDef = x ++ y
  }

  class BindingDSLSemilattice extends BoundedSemilattice[BindingDSL] {
    def empty: BindingDSL = TrivialModuleDef
    def combine(x: BindingDSL, y: BindingDSL): BindingDSL = x ++ y
  }

}
