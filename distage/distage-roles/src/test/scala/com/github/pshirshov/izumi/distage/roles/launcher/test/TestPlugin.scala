package com.github.pshirshov.izumi.distage.roles.launcher.test

import java.util.concurrent.{ExecutorService, Executors}

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.launcher.{AbstractConfigWriter, ConfigWriter}
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.distage.roles.roles.{BackendPluginTags, RoleId, RoleService, RolesInfo}
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.logstage.api.IzLogger

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
                      ) extends AbstractConfigWriter[TestPlugin](logger, launcherVersion, roleInfo, config)

class TestPlugin extends PluginDef {
  tag(BackendPluginTags.Production)
  make[ArtifactVersion].named("launcher-version").from(ArtifactVersion("0.0.0-SNAPSHOT"))
  make[RoleService].named("testservice").from[TestService]
  make[TestConfigWriter]
  many[Dummy]
  make[NotCloseable].from[InheritedCloseable]

}

class ResourcesPlugin extends PluginDef {
  make[InitCounter]

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
