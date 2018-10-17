package com.github.pshirshov.izumi.distage

import cats.kernel.instances.set._
import cats.kernel.{BoundedSemilattice, Hash, Monoid, PartialOrder}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase, ModuleMake}
import distage.{ModuleBase, SemiPlan}

trait ModuleBaseInstances {

  implicit def catsKernelStdPartialOrderHashForModuleBase[T <: ModuleBase]: PartialOrder[T] with Hash[T] =
    new PartialOrder[T] with Hash[T] {
      override def partialCompare(x: T, y: T): Double = PartialOrder[Set[Binding]].partialCompare(x.bindings, y.bindings)
      override def hash(x: T): Int = x.hashCode()
      override def eqv(x: T, y: T): Boolean = x == y
    }

  implicit def catsKernelStdSemilatticeForModuleBase[T <: ModuleBase.Aux[T]: ModuleMake]: BoundedSemilattice[T] =
    new ModuleBaseSemilattice

  private class ModuleBaseSemilattice[T <: ModuleBase.Aux[T]: ModuleMake] extends BoundedSemilattice[T] {
    def empty: T = ModuleMake[T].empty
    def combine(x: T, y: T): T = x ++ y
  }

}

trait SemiPlanInstances {

  implicit def catsKernelStdHashForSemiPlan: Hash[SemiPlan] =
    new Hash[SemiPlan] {
      override def hash(x: SemiPlan): Int = x.hashCode()
      override def eqv(x: SemiPlan, y: SemiPlan): Boolean = x == y
    }

  implicit def catsKernelStdMonoidForSemiPlan: Monoid[SemiPlan] =
    new Monoid[SemiPlan] {
      override def empty: SemiPlan = SemiPlan(ModuleBase.empty, Vector.empty)

      override def combine(x: SemiPlan, y: SemiPlan): SemiPlan = x ++ y
    }
}
