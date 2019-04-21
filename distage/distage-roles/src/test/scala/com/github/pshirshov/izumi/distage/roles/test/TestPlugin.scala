package com.github.pshirshov.izumi.distage.roles.test

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.IO
import com.github.pshirshov.izumi.distage.app.DIAppStartupContext
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.distage.roles.launcher.{AbstractConfigWriter, ConfigWriter}
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.distage.roles.test.Junk._


class TestPlugin extends PluginDef {
  import TestPlugin._
  tag(BackendPluginTags.Production)

  addImplicit[DIEffect[IO]]
  addImplicit[DIEffectRunner[IO]]

  make[ArtifactVersion].named("launcher-version").from(ArtifactVersion(s"0.0.0-${System.currentTimeMillis()}"))
  many[Dummy]

  make[TestTask00[IO]]
  make[TestRole00[IO]]
  make[TestRole01[IO]]
  make[TestRole02[IO]]


  make[NotCloseable].from[InheritedCloseable]
  make[TestConfigWriter]
}

object TestPlugin {
  trait NotCloseable

  class InheritedCloseable extends NotCloseable with AutoCloseable {
    override def close(): Unit = {}
  }

  @RoleId(ConfigWriter.id)
  class TestConfigWriter(
                          logger: IzLogger,
                          launcherVersion: ArtifactVersion@Id("launcher-version"),
                          roleInfo: RolesInfo,
                          config: WriteReference,
                          context: DIAppStartupContext
                        ) extends AbstractConfigWriter[TestPlugin](logger, launcherVersion, roleInfo, config, context)
}


class ResourcesPlugin extends PluginDef {
  import ResourcesPlugin._
  make[InitCounter]
  make[Conflict].tagged(BackendPluginTags.Dummy).from[C1]
  make[Conflict].tagged(BackendPluginTags.Production).from[C2]

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
