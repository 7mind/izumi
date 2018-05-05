package com.github.pshirshov.test.testapp

import com.github.pshirshov.izumi.distage.plugins.PluginBuilder

class TestApp(conflict: Conflict) {}

class BadApp {}

class DisabledBinding {}
trait DisabledTrait {}
class DisabledImpl extends DisabledTrait {}

trait Conflict {}
class ConflictA extends Conflict {}
class ConflictB extends Conflict {}

class TestPlugin extends PluginBuilder {
  bind[TestApp]
  bind[DisabledBinding]
  bind[DisabledTrait].as[DisabledImpl]
  bind[DisabledTrait].as[DisabledImpl]
  bind[Conflict].as[ConflictA]
  bind[Conflict].as[ConflictB]
}

class BadPlugin extends PluginBuilder {
  bind[BadApp]
}
