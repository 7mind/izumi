package com.github.pshirshov.izumi.distage.model.definition

trait Module extends ModuleBase {

}

object Module {
  def empty: Module = new Module {
    override def bindings: Set[Binding] = Set.empty
  }

  def simple(bindings: Set[Binding]): Module = {
    val b = bindings
    new Module {
      override def bindings: Set[Binding] = b
    }
  }

  implicit object ModuleOps extends ModuleApi[Module] {
    override def empty: Module = Module.empty

    override def simple(bindings: Set[Binding]): Module = Module.simple(bindings)
  }
}

trait ModuleDef extends DSLModuleDef with Module {

}

object ModuleDef {
  def empty: ModuleDef = new ModuleDef {
    override def bindings: Set[Binding] = Set.empty
  }

  def simple(bindings: Set[Binding]): ModuleDef = {
    val b = bindings
    new ModuleDef {
      override def bindings: Set[Binding] = b
    }
  }

  implicit object ModuleDefOps extends ModuleApi[ModuleDef] {
    override def empty: ModuleDef = ModuleDef.empty

    override def simple(bindings: Set[Binding]): ModuleDef = ModuleDef.simple(bindings)
  }
}
