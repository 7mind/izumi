package izumi.idealingua.model.publishing.manifests

import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.BuildManifest.{Common, ManifestDependency}


sealed trait GoProjectLayout

object GoProjectLayout {

  final case object REPOSITORY extends GoProjectLayout

  final case object PLAIN extends GoProjectLayout

  // TODO: we would have to support go modules at ~ go 1.13: https://github.com/golang/go/wiki/Modules

}

case class GoRepositoryOptions(
                                dependencies: List[ManifestDependency],
                                repository: String,
                       )

object GoRepositoryOptions {
  def example: GoRepositoryOptions = GoRepositoryOptions(
    dependencies = List(ManifestDependency("github.com/gorilla/websocket", "")),
    repository = "github.com/TestCompany/TestRepo",
  )
}


case class GoLangBuildManifest(
                                common: Common,
                                layout: GoProjectLayout,
                                repository: GoRepositoryOptions,
                                enableTesting: Boolean
                              ) extends BuildManifest

object GoLangBuildManifest {
  def example: GoLangBuildManifest = {
    GoLangBuildManifest(
      common = BuildManifest.Common.example,
      layout = GoProjectLayout.REPOSITORY,
      repository = GoRepositoryOptions.example,
      enableTesting = true
    )
  }

  def importPrefix(manifest: GoLangBuildManifest): String = {
    if (manifest.layout != GoProjectLayout.REPOSITORY || manifest.repository.repository.isEmpty) {
      ""
    } else {
      if (manifest.repository.repository.endsWith("/")) manifest.repository.repository else s"${manifest.repository.repository}/"
    }
  }
}
