package izumi.idealingua.model.publishing.manifests

import izumi.idealingua.model.publishing.{BuildManifest, ProjectNamingRule}
import izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}

sealed trait TypeScriptProjectLayout

object TypeScriptProjectLayout {

  final case object YARN extends TypeScriptProjectLayout

  final case object PLAIN extends TypeScriptProjectLayout

}

case class YarnOptions(
                        projectNaming: ProjectNamingRule,
                        scope: String,
                        dependencies: List[ManifestDependency],
                        devDependencies: List[ManifestDependency],
                      )

object YarnOptions {
  def example: YarnOptions = YarnOptions(
    projectNaming = ProjectNamingRule.example,
    dependencies = List(
      ManifestDependency("moment", "^2.20.1"),
      ManifestDependency("@types/node", "^10.7.1"),
      ManifestDependency("@types/websocket", "0.0.39"),
    ),
    devDependencies = List(
      ManifestDependency("typescript", "3.2.2"),
    ),
    scope = "@TestScope",
  )
}

// https://docs.npmjs.com/files/package.json
// https://github.com/npm/node-semver#prerelease-tags
case class TypeScriptBuildManifest(
                                    common: Common,
                                    layout: TypeScriptProjectLayout,
                                    yarn: YarnOptions
                                  ) extends BuildManifest

object TypeScriptBuildManifest {
  def example: TypeScriptBuildManifest = {
    val common = BuildManifest.Common.example
    TypeScriptBuildManifest(
      common = common.copy(version = common.version.copy(snapshotQualifier = "build.0")),
      layout = TypeScriptProjectLayout.YARN,
      yarn = YarnOptions.example,
    )
  }
}
