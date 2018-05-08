package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding.{ImplBinding, SetElementBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef, TrivialModuleDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait AutoSetHook extends PlanningHookDefaultImpl {
  def elementOf(b: Binding): Option[RuntimeDIUniverse.DIKey]

  override def hookDefinition(defn: ModuleDef): ModuleDef = {

    val autoSetsElements = defn.bindings.flatMap {
      b =>
        elementOf(b)
          .map {
            key =>
              b match {
                case i: ImplBinding =>
                  SetElementBinding(key, i.implementation, Set("izumi.autoset"))

              }
          }
          .toSet
    }

    TrivialModuleDef(defn.bindings ++ autoSetsElements)
  }
}
