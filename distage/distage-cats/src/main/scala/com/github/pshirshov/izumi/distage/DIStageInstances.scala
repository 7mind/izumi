package com.github.pshirshov.izumi.distage

import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import cats.kernel.instances.set.catsKernelStdPartialOrderForSet
import com.github.pshirshov.izumi.distage.model.definition.{ContextDefinition, TrivialDIDef}

object DIStageInstances {
  implicit val catsKernelStdPartialOrderForContextDefinition: PartialOrder[ContextDefinition] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForContextDefinition: BoundedSemilattice[ContextDefinition] =
    new ContextDefinitionSemilattice

  implicit val catsKernelStdHashForContextDefinition: Hash[ContextDefinition] =
    Hash.fromUniversalHashCode[ContextDefinition]
}

class ContextDefinitionSemilattice extends BoundedSemilattice[ContextDefinition] {
  def empty: ContextDefinition = TrivialDIDef
  def combine(x: ContextDefinition, y: ContextDefinition): ContextDefinition = x ++ y
}
