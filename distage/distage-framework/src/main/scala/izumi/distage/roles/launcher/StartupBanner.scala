package izumi.distage.roles.launcher

import izumi.distage.framework.services._
import izumi.distage.roles.model.meta.LibraryReference
import izumi.fundamentals.platform.resources.IzManifest
import izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

trait StartupBanner {
  def showBanner(logger: IzLogger): Unit
}

object StartupBanner {
  class StartupBannerImpl(
    referenceLibraries: Set[LibraryReference]
  ) extends StartupBanner {
    def showBanner(logger: IzLogger): Unit = {
      val withIzumi = LibraryReference("izumi", classOf[ConfigLoader]) +: referenceLibraries.toSeq.sortBy(_.libraryName)
      showDepData(logger, "Application is about to start", this.getClass)
      withIzumi.foreach {
        lib => showDepData(logger, s"... using ${lib.libraryName}", lib.clazz)
      }
    }

    private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
      val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
      val details = mf.getOrElse("{No version data}")
      logger.info(s"$msg : $details")
    }
  }
}
