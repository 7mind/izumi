package izumi.distage.roles.test.fixtures

import java.util.concurrent.{ExecutorService, Executors}

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures.ResourcesPlugin._

class ResourcesPluginBase extends ModuleDef {
  make[Conflict].tagged(Env.Prod).from[Conflict1]
  make[Conflict].tagged(Env.Test).from[Conflict2]
  make[Conflict].from[Conflict3]

  make[UnsolvableConflict].from[UnsolvableConflict1]
  make[UnsolvableConflict].from[UnsolvableConflict2]

  make[ExecutorService].from(Executors.newCachedThreadPool())
  make[Resource2]
  make[Resource3]
  make[Resource4]
  make[Resource5]
  make[Resource6]

  many[Resource]
    .ref[Resource2]
    .ref[Resource3]
    .ref[Resource4]
    .ref[Resource5]
    .ref[Resource6]
}

class ResourcesPlugin extends ResourcesPluginBase with PluginDef {
  make[InitCounter]

  make[Resource1]
  many[Resource]
    .ref[Resource1]
}

object ResourcesPlugin {
  trait Conflict
  case class Conflict1() extends Conflict
  case class Conflict2(u: UnsolvableConflict) extends Conflict
  case class Conflict3() extends Conflict

  trait UnsolvableConflict
  class UnsolvableConflict1 extends UnsolvableConflict
  class UnsolvableConflict2 extends UnsolvableConflict
}
