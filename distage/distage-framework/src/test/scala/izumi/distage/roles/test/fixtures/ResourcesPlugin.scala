package izumi.distage.roles.test.fixtures

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.IO
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

trait ResourcesPluginBase extends ModuleDef {
  make[ExecutorService].from(Executors.newCachedThreadPool())
  make[IntegrationResource1[IO]]
  make[JustResource1[IO]]
  make[JustResource2[IO]]
  make[ProbeResource0[IO]]
  make[JustResource3[IO]]

  many[TestResource]
    .ref[IntegrationResource1[IO]]
    .ref[JustResource1[IO]]
    .ref[JustResource2[IO]]
    .ref[ProbeResource0[IO]]
    .ref[JustResource3[IO]]
}

class ResourcesPlugin extends PluginDef with ResourcesPluginBase {
  make[XXX_ResourceEffectsRecorder[IO]]

  make[IntegrationResource0[IO]]
  many[TestResource]
    .ref[IntegrationResource0[IO]]
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
