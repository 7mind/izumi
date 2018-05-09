package com.github.pshirshov.test.testapp

import com.github.pshirshov.izumi.distage.config.AutoConf
import com.github.pshirshov.izumi.distage.config.pureconfig.WithPureConfig
import com.github.pshirshov.izumi.distage.config.pureconfig.WithPureConfig.R
import com.github.pshirshov.izumi.distage.plugins.PluginDef

case class Config(value: String)

object Config extends WithPureConfig[Config] {
  override def reader: R[Config] = implicitly
}

class TestApp(
               val conflict: Conflict
               , @AutoConf val config: Config
               , val setTest: Set[Conflict]
             ) {

}

class BadApp {}

class DisabledBinding {}
trait DisabledTrait {}
class DisabledImpl extends DisabledTrait {}

trait Conflict {}
class ConflictA extends Conflict {}
class ConflictB extends Conflict {}

class TestPlugin extends PluginDef {
  make[TestApp]
  make[DisabledBinding]
  make[DisabledTrait].from[DisabledImpl]
  make[DisabledTrait].from[DisabledImpl]
  make[Conflict].from[ConflictA]
  make[Conflict].from[ConflictB]
}

class BadPlugin extends PluginDef {
  make[BadApp]
}
