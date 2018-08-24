package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set._
import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import com.github.pshirshov.izumi.distage.model.definition.Module
import distage.ModuleBase

trait ModuleBaseInstances {

  implicit val catsKernelStdPartialOrderForModuleBase: PartialOrder[ModuleBase] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForModuleBase: BoundedSemilattice[ModuleBase] =
    new ModuleBaseSemilattice

  implicit val catsKernelStdHashForModuleBase: Hash[ModuleBase] =
    Hash.fromUniversalHashCode

  class ModuleBaseSemilattice extends BoundedSemilattice[ModuleBase] {
    def empty: ModuleBase = Module.empty
    def combine(x: ModuleBase, y: ModuleBase): ModuleBase = x ++ y
  }

}
