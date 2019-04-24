package com.github.pshirshov.izumi.distage.roles.test.fixtures

import cats.effect.IO
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.internal.{ConfigWriter, Help}
import com.github.pshirshov.izumi.distage.roles.model.BackendPluginTags
import com.github.pshirshov.izumi.distage.roles.test.fixtures.Junk._
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion


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
  make[ConfigWriter[IO]]
  make[Help[IO]]
}

object TestPlugin {

  trait NotCloseable

  class InheritedCloseable extends NotCloseable with AutoCloseable {
    override def close(): Unit = {}
  }

}
