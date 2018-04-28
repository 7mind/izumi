package com.github.pshirshov.izumi.distage

import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import cats.kernel.instances.set.catsKernelStdPartialOrderForSet
import com.github.pshirshov.izumi.distage.model.definition.{ModuleDef, TrivialModuleDef}

object DIStageInstances {
  implicit val catsKernelStdPartialOrderForContextDefinition: PartialOrder[ModuleDef] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForContextDefinition: BoundedSemilattice[ModuleDef] =
    new ContextDefinitionSemilattice

  implicit val catsKernelStdHashForContextDefinition: Hash[ModuleDef] =
    Hash.fromUniversalHashCode[ModuleDef]
}

class ContextDefinitionSemilattice extends BoundedSemilattice[ModuleDef] {
  def empty: ModuleDef = TrivialModuleDef
  def combine(x: ModuleDef, y: ModuleDef): ModuleDef = x ++ y
}
