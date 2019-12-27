package izumi.distage.roles.test.fixtures

import java.util.concurrent.{ExecutorService, Executors}

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures.ResourcesPlugin._

class ConflictPlugin extends PluginDef {
  make[Conflict].tagged(Env.Prod).from[Conflict1]
  make[Conflict].tagged(Env.Test).from[Conflict2]
  make[Conflict].from[Conflict3]

  make[UnsolvableConflict].from[UnsolvableConflict1]
  make[UnsolvableConflict].from[UnsolvableConflict2]
}

class ResourcesPluginBase extends ModuleDef {
  make[ExecutorService].from(Executors.newCachedThreadPool())
  make[IntegrationResource1]
  make[JustResource1]
  make[JustResource2]
  make[ProbeResource0]
  make[JustResource3]

  many[TestResource]
    .ref[IntegrationResource1]
    .ref[JustResource1]
    .ref[JustResource2]
    .ref[ProbeResource0]
    .ref[JustResource3]
}

class ResourcesPlugin extends ResourcesPluginBase with PluginDef {
  make[XXX_ResourceEffectsRecorder]

  make[IntegrationResource0]
  many[TestResource]
    .ref[IntegrationResource0]
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
