package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}


case class ScalaBuildManifest(name: String,
                              tags: String,
                              description: String,
                              notes: String,
                              publisher: Publisher,
                              version: String,
                              license: String,
                              website: String,
                              copyright: String,
                              dependencies: List[ManifestDependency],
                              layout: ScalaProjectLayout,
                              dropPackageHead: Int,
                          ) extends BuildManifest

object ScalaBuildManifest {
  def default: ScalaBuildManifest = ScalaBuildManifest(
    name = "TestBuild",
    tags = "",
    description = "Test Description",
    notes = "",
    publisher = Publisher("Test Publisher Name", "test_publisher_id"),
    version = "0.0.0",
    license = "MIT",
    website = "http://project.website",
    copyright = "Copyright (C) Test Inc.",
    dependencies = List.empty,
    layout = ScalaProjectLayout.PLAIN,
    dropPackageHead = 0,
  )
}


sealed trait ScalaProjectLayout

object ScalaProjectLayout {
  final case object PLAIN extends ScalaProjectLayout
  final case object SBT extends ScalaProjectLayout
}
