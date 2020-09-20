package izumi.distage.roles.test.fixtures

import cats.effect.IO
import distage.Id
import izumi.distage.config.ConfigModuleDef
import izumi.distage.effect.modules.CatsDIEffectModule
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.bundled.{ConfigWriter, Help}
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.test.fixtures.Fixture._
import izumi.distage.roles.test.fixtures.TestPlugin.{InheritedCloseable, NotCloseable}
import izumi.distage.roles.test.fixtures.roles.TestRole00
import izumi.distage.roles.test.fixtures.roles.TestRole00.{IntegrationOnlyCfg, IntegrationOnlyCfg2, SetElementOnlyCfg, TestRole00Resource, TestRole00ResourceIntegrationCheck}
import izumi.fundamentals.platform.resources.ArtifactVersion
import izumi.reflect.TagK

class TestPluginBase[F[_]: TagK] extends PluginDef with ConfigModuleDef with RoleModuleDef {
  tag(Mode.Prod)

  include(CatsDIEffectModule)

  private def version = Option(System.getProperty(TestPlugin.versionProperty)) match {
    case Some(value) =>
      value
    case None =>
      s"0.0.0-${System.currentTimeMillis()}"
  }

  make[ArtifactVersion].named("launcher-version").from(ArtifactVersion(version))
  many[Dummy]

  many[SetElement]
    .add[SetElement1]

  makeRole[TestTask00[F]]
  makeRole[TestRole00[F]]
  make[TestRole01[F]]
  make[TestRole02[F]]
  make[TestRole03[F]]
  make[TestRole04[F]]

  make[TestRole00Resource[F]]
  make[TestRole00ResourceIntegrationCheck[F]]

  make[NotCloseable].from[InheritedCloseable]
  make[ConfigWriter[F]]
  make[Help[F]]

  make[AxisComponent].from(AxisComponentCorrect).tagged(AxisComponentAxis.Correct)
  make[AxisComponent].from(AxisComponentIncorrect).tagged(AxisComponentAxis.Incorrect)

  makeConfig[TestServiceConf]("testservice")
  makeConfig[IntegrationOnlyCfg]("integrationOnlyCfg")
  makeConfig[SetElementOnlyCfg]("setElementConfig")

  makeConfigNamed[IntegrationOnlyCfg2]("integrationOnlyCfg2")
  make[IntegrationOnlyCfg2].from {
    conf: IntegrationOnlyCfg2 @Id("integrationOnlyCfg2") =>
      IntegrationOnlyCfg2(conf.value + ":updated")
  }

  makeConfigNamed[TestServiceConf2]("testservice2")
  make[TestServiceConf2].from {
    conf: TestServiceConf2 @Id("testservice2") =>
      TestServiceConf2(conf.strval + ":updated")
  }
  makeConfig[ListConf]("listconf")
}

class TestPlugin extends TestPluginBase[IO]

object TestPlugin {
  trait NotCloseable
  final val versionProperty = "launcher-version-test"

  class InheritedCloseable extends NotCloseable with AutoCloseable {
    override def close(): Unit = {}
  }
}
