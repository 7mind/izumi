package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set._
import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import distage.{ModuleBase, SimpleModuleDef}

trait ModuleBaseInstances {

  implicit val catsKernelStdPartialOrderForModuleBase: PartialOrder[ModuleBase] =
    PartialOrder.by(_.bindings)

  implicit val catsKernelStdSemilatticeForModuleBase: BoundedSemilattice[ModuleBase] =
    new ModuleBaseSemilattice

  implicit val catsKernelStdHashForModuleBase: Hash[ModuleBase] =
    Hash.fromUniversalHashCode

  class ModuleBaseSemilattice extends BoundedSemilattice[ModuleBase] {
    def empty: ModuleBase = SimpleModuleDef(Set.empty)
    def combine(x: ModuleBase, y: ModuleBase): ModuleBase = x ++ y
  }

}
