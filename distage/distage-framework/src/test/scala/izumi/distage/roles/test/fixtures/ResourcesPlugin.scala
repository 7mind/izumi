package izumi.distage.roles.test.fixtures

import cats.effect.IO
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.*
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.test.fixtures.Fixture.*
import izumi.distage.roles.test.fixtures.ResourcesPlugin.*
import izumi.functional.bio.Bifunctorized

import java.util.concurrent.{ExecutorService, Executors}

private object ResourcesPluginTypes {
  type BIO[+E, +A] = Bifunctorized[IO, E, A]
  type IdentityB[+E, +A] = Bifunctorized.IdentityBifunctorized[E, A]
}
import izumi.distage.roles.test.fixtures.ResourcesPluginTypes.{BIO, IdentityB}

class ConflictPlugin extends PluginDef {
  make[Conflict].tagged(Mode.Prod).from[Conflict1]
  make[Conflict].tagged(Mode.Test).from[Conflict2]
  make[Conflict].from[Conflict3]

  make[UnsolvableConflict].from[UnsolvableConflict1]
  make[UnsolvableConflict].from[UnsolvableConflict2]
}

trait ResourcesPluginBase extends ModuleDef {
  make[ExecutorService].from(Executors.newCachedThreadPool())

  make[IntegrationResource1[IdentityB]]
  make[JustResource1[IdentityB]]
  make[JustResource2[IdentityB]]
  make[ProbeResource0[IdentityB]]
  make[JustResource3[IdentityB]]

  many[TestResource[IdentityB]]
    .ref[IntegrationResource1[IdentityB]]
    .ref[JustResource1[IdentityB]]
    .ref[JustResource2[IdentityB]]
    .ref[ProbeResource0[IdentityB]]
    .ref[JustResource3[IdentityB]]

  make[IntegrationResource1[BIO]]
  make[JustResource1[BIO]]
  make[JustResource2[BIO]]
  make[ProbeResource0[BIO]]
  make[JustResource3[BIO]]

  many[TestResource[BIO]]
    .ref[IntegrationResource1[BIO]]
    .ref[JustResource1[BIO]]
    .ref[JustResource2[BIO]]
    .ref[ProbeResource0[BIO]]
    .ref[JustResource3[BIO]]
}

class ResourcesPlugin extends PluginDef with ResourcesPluginBase {
  make[XXX_ResourceEffectsRecorder[BIO]]

  make[IntegrationResource0[BIO]]
  many[TestResource[BIO]]
    .ref[IntegrationResource0[BIO]]
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
