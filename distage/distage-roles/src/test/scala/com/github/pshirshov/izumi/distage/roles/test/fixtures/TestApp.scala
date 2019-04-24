package com.github.pshirshov.izumi.distage.roles.test.fixtures

import com.github.pshirshov.izumi.distage.config.annotations.AutoConf
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

case class Config(value: String)

class TestApp(
               val conflict: Conflict
               , @AutoConf val config: Config
               , val setTest: Set[Conflict]
               , app: => TestApp
             ) {
  Quirks.forget(app)
}

class BadApp


class DisabledByGc


trait DisabledByTag
trait WithGoodTag

trait DisabledByKey
class DisabledImplForByKeyTrait extends DisabledByKey

trait DisabledByImpl
class DisabledImplForByImplTrait extends DisabledByImpl

trait Conflict
class ConflictA extends Conflict
class ConflictB extends Conflict

trait SetEl
class SetEl1 extends SetEl

trait WeakSetTest
class WeakSetGood extends WeakSetTest
class WeakSetBad extends WeakSetTest
class WeakSetStrong extends WeakSetTest

case class WeakSetDep(s1: Set[WeakSetTest], g1: WeakSetGood)

class TestAppPlugin extends PluginDef {
  make[TestApp]

  make[SetEl1]
  many[SetEl].ref[SetEl1]

  make[DisabledByGc]

  make[WeakSetDep]
  make[WeakSetGood]
  make[WeakSetBad]
  make[WeakSetStrong]
  many[WeakSetTest]
      .weak[WeakSetGood]
      .weak[WeakSetBad]
      .ref[WeakSetStrong]

  make[DisabledByTag].tagged("badtag")
  make[WithGoodTag].tagged("goodtag")

  make[DisabledByKey].from[DisabledImplForByKeyTrait]
  make[DisabledByImpl].from[DisabledImplForByImplTrait]

  make[Conflict].from[ConflictA]
//  make[Conflict].from[ConflictB]
}

class BadPlugin extends PluginDef {
  make[BadApp]
}
