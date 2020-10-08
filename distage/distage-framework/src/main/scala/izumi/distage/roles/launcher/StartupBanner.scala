package izumi.distage.roles.launcher

import izumi.distage.roles.model.meta.LibraryReference
import distage.Id
import izumi.fundamentals.platform.resources.{IzArtifact, IzArtifactMaterializer}
import izumi.logstage.api.IzLogger

trait StartupBanner {
  def showBanner(logger: IzLogger): Unit
}

object StartupBanner {
  class Impl(
    referenceLibraries: Set[LibraryReference],
    appArtifact: Option[IzArtifact] @Id("app.artifact"),
  ) extends StartupBanner {
    def showBanner(logger: IzLogger): Unit = {
      showDepData(logger, "Application is about to start", appArtifact)

      val withIzumi = LibraryReference("izumi", Some(IzArtifactMaterializer.currentArtifact)) +: referenceLibraries.toSeq.sortBy(_.libraryName)
      withIzumi.foreach {
        lib =>
          showDepData(logger, s"... using ${lib.libraryName}", lib.artifact)
      }
    }

    private[this] def showDepData(logger: IzLogger, msg: String, clazz: Option[IzArtifact]): Unit = {
      val details = clazz.map(_.toString).getOrElse("{No version data}")
      logger.info(s"$msg : $details")
    }
  }
}
