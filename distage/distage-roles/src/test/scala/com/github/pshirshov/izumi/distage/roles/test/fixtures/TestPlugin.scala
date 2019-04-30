package com.github.pshirshov.izumi.distage.roles.test.fixtures

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.definition.EnvAxis
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.internal.{ConfigWriter, Help}
import com.github.pshirshov.izumi.distage.roles.test.fixtures.Junk._
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion


class TestPlugin extends PluginDef {
  import TestPlugin._
  tag(EnvAxis.Production)

  addImplicit[DIEffect[IO]]
  addImplicit[DIEffectRunner[IO]]

  private val version = Option(System.getProperty(TestPlugin.versionProperty)) match {
    case Some(value) =>
      value
    case None =>
      s"0.0.0-${System.currentTimeMillis()}"
  }

  make[ArtifactVersion].named("launcher-version").from(ArtifactVersion(version))
  many[Dummy]

  make[TestTask00[IO]]
  make[TestRole00[IO]]
  make[TestRole01[IO]]
  make[TestRole02[IO]]


  make[NotCloseable].from[InheritedCloseable]
  make[ConfigWriter[IO]]
  make[Help[IO]]
}

object TestPlugin {

  trait NotCloseable

  final val versionProperty = "launcher-version-test"
  class InheritedCloseable extends NotCloseable with AutoCloseable {
    override def close(): Unit = {}
  }

}
