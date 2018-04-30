package com.github.pshirshov.izumi.distage

import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import cats.kernel.instances.set.catsKernelStdPartialOrderForSet
import com.github.pshirshov.izumi.distage.model.definition.{AbstractModuleDef, TrivialModuleDef}

object DIStageInstances {
  implicit val catsKernelStdPartialOrderForContextDefinition: PartialOrder[AbstractModuleDef] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForContextDefinition: BoundedSemilattice[AbstractModuleDef] =
    new ContextDefinitionSemilattice

  implicit val catsKernelStdHashForContextDefinition: Hash[AbstractModuleDef] =
    Hash.fromUniversalHashCode[AbstractModuleDef]
}

class ContextDefinitionSemilattice extends BoundedSemilattice[AbstractModuleDef] {
  def empty: AbstractModuleDef = TrivialModuleDef
  def combine(x: AbstractModuleDef, y: AbstractModuleDef): AbstractModuleDef = x ++ y
}
