package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}


case class ScalaBuildManifest(
                               common: Common,
                               dependencies: List[ManifestDependency],
                               layout: ScalaProjectLayout,

                               /**
                                 * Positive value will work as .drop on fully qualified module name
                                 * Zero value will leave name untouched
                                 * Negative value will work as .takeRight
                                 *
                                 * Does not apply for layout == PLAIN
                                 */
                               dropPackageHead: Int,
                             ) extends BuildManifest

object ScalaBuildManifest {
  def default: ScalaBuildManifest = ScalaBuildManifest(
    common = BuildManifest.Common.default,
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
