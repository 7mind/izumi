package com.github.pshirshov.test.app

import com.github.pshirshov.izumi.distage.model.definition.{Binding, PluginDef, TrivialModuleDef}

class TestApp {}

class BadApp {}

class TestPlugin extends PluginDef {
  override def bindings: Set[Binding] = {
    TrivialModuleDef.bind[TestApp].bindings
  }
}

class BadPlugin extends PluginDef {
  override def bindings: Set[Binding] = {
    TrivialModuleDef.bind[BadApp].bindings
  }
}
