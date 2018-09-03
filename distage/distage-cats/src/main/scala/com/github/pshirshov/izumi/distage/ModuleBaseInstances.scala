package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set._
import cats.kernel.{BoundedSemilattice, Hash, PartialOrder}
import com.github.pshirshov.izumi.distage.model.definition.ModuleMake
import distage.ModuleBase

trait ModuleBaseInstances {

  implicit def catsKernelStdPartialOrderForModuleBase[T <: ModuleBase]: PartialOrder[T] =
    PartialOrder.by(_.bindings)

  implicit def catsKernelStdSemilatticeForModuleBase[T <: ModuleBase: ModuleMake]: BoundedSemilattice[T] =
    new ModuleBaseSemilattice

  implicit def catsKernelStdHashForModuleBase[T <: ModuleBase]: Hash[ModuleBase] =
    Hash.fromUniversalHashCode

  private class ModuleBaseSemilattice[T <: ModuleBase: ModuleMake] extends BoundedSemilattice[T] {
    def empty: T = ModuleMake[T].empty
    def combine(x: T, y: T): T= x ++ y
  }

}
