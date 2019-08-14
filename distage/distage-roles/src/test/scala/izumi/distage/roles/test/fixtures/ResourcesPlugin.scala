package izumi.distage.roles.test.fixtures

import java.util.concurrent.{ExecutorService, Executors}

import izumi.distage.model.definition.StandardAxis._
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.test.fixtures.Junk._

class ResourcesPlugin extends PluginDef {
  import ResourcesPlugin._
  make[InitCounter]
  make[Conflict].tagged(Env.Prod).from[Conflict1]
  make[Conflict].tagged(Env.Test).from[Conflict2]
  make[UnsolvableConflict].from[UnsolvableConflict1]
  make[UnsolvableConflict].from[UnsolvableConflict2]

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
  case class Conflict1() extends Conflict
  case class Conflict2(u: UnsolvableConflict) extends Conflict

  trait UnsolvableConflict
  class UnsolvableConflict1 extends UnsolvableConflict
  class UnsolvableConflict2 extends UnsolvableConflict

}
