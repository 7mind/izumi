package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}


case class ScalaBuildManifest(
                               common: Common,
                               layout: ScalaProjectLayout,
                               sbt: SbtOptions,
                             ) extends BuildManifest

case class SbtOptions(
                       /**
                         * Positive value will work as .drop on fully qualified module name
                         * Zero value will leave name untouched
                         * Negative value will work as .takeRight
                         *
                         * Does not apply for layout == PLAIN
                         */
                       dropFQNSegments: Option[Int],
                       projectIdPostfix: Option[String],
                     )

object SbtOptions {
  def example: SbtOptions = {
    SbtOptions(
      dropFQNSegments = Some(0),
      projectIdPostfix = Some("api"),
    )
  }
}

object ScalaBuildManifest {
  def example: ScalaBuildManifest = {
    val common = BuildManifest.Common.example
    ScalaBuildManifest(
      common = common.copy(version = common.version.copy(snapshotQualifier = "SNAPSHOT")),
      layout = ScalaProjectLayout.SBT,
      sbt = SbtOptions.example,
    )
  }
}


sealed trait ScalaProjectLayout

object ScalaProjectLayout {

  final case object PLAIN extends ScalaProjectLayout

  final case object SBT extends ScalaProjectLayout

}
