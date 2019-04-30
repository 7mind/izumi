package com.github.pshirshov.izumi.distage.roles.test.fixtures

import java.util.concurrent.{ExecutorService, Executors}

import com.github.pshirshov.izumi.distage.model.definition.EnvAxis
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.test.fixtures.Junk._

class ResourcesPlugin extends PluginDef {
  import ResourcesPlugin._
  make[InitCounter]
  make[Conflict].tagged(EnvAxis.Mock).from[C1]
  make[Conflict].tagged(EnvAxis.Production).from[C2]

  make[ExecutorService].from(Executors.newCachedThreadPool())
  make[Resource1]
  make[Resource2]
  make[Resource3]
  make[Resource4]
  make[Resource5]
  make[Resource6]

  many[Resource]
    .ref[Resource1]
    .ref[Resource2]
    .ref[Resource3]
    .ref[Resource4]
    .ref[Resource5]
    .ref[Resource6]
}

object ResourcesPlugin {
  trait Conflict

  class C1 extends Conflict
  class C2 extends Conflict

}
