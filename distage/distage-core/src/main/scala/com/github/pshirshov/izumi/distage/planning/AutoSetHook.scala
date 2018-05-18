package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding.{ImplBinding, SetElementBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase, SimpleModuleDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait AutoSetHook extends PlanningHookDefaultImpl {
  def elementOf(b: Binding): Option[RuntimeDIUniverse.DIKey]

  override def hookDefinition(defn: ModuleBase): ModuleBase = {

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
    }

    SimpleModuleDef(defn.bindings ++ autoSetsElements)
  }
}

