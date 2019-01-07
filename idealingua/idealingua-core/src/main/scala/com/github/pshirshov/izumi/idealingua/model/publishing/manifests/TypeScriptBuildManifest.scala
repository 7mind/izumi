package com.github.pshirshov.izumi.idealingua.model.publishing.manifests

import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}

sealed trait TypeScriptProjectLayout

object TypeScriptProjectLayout {

  final case object YARN extends TypeScriptProjectLayout

  final case object PLAIN extends TypeScriptProjectLayout

}

// https://docs.npmjs.com/files/package.json
// https://github.com/npm/node-semver#prerelease-tags
case class TypeScriptBuildManifest(
                                    common: Common,
                                    dependencies: List[ManifestDependency],
                                    scope: String,
                                    layout: TypeScriptProjectLayout,
                                    /** This one only works with scoped namespaces, this way you can
                                      * get rid of @scope/net-company-project and use @scope/project
                                      * by using dropnameSpaceSegments = Some(2)
                                      */
                                    dropFQNSegments: Option[Int],
                                  ) extends BuildManifest

object TypeScriptBuildManifest {
  def example: TypeScriptBuildManifest = {
    val common = BuildManifest.Common.example
    TypeScriptBuildManifest(
      common = common.copy(version = common.version.copy(snapshotQualifier = "build.0")),
      dependencies = List(
        ManifestDependency("moment", "^2.20.1"),
        ManifestDependency("@types/node", "^10.7.1"),
        ManifestDependency("@types/websocket", "0.0.39"),
      ),
      scope = "@TestScope",
      layout = TypeScriptProjectLayout.YARN,
      dropFQNSegments = None
    )
  }
}
