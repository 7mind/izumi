package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding.SetElementBinding
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef, TrivialModuleDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait AutoSetHook extends PlanningHookDefaultImpl {
  def elementOf(b: Binding): Option[RuntimeDIUniverse.DIKey]

  override def hookDefinition(defn: ModuleDef): ModuleDef = {

    val autoSetsElements = defn.bindings.flatMap {
      b =>
        elementOf(b).map(key => key -> b.key).toSet
    }

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
    val autoSets = autoSetsElements.toMultimap
    val autoBindings = autoSets.map(as => SetElementBinding(as._1, ???, Set("izumi.autoset")))

    TrivialModuleDef(defn.bindings ++ autoBindings)
  }
}
