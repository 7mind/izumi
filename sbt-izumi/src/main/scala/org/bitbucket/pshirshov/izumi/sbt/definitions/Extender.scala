package org.bitbucket.pshirshov.izumi.sbt.definitions

import org.bitbucket.pshirshov.izumi.sbt.definitions.IzumiScopes.ProjectReferenceEx
import sbt.Keys._
import sbt.Project
import sbt.internal.util.ConsoleLogger

trait Extender {
  protected val logger: ConsoleLogger = ConsoleLogger()

  def extend(p: Project): Project
}

class GlobalSettingsExtender(settings: GlobalSettings) extends Extender {
  override def extend(p: Project) = {
    p.settings(settings.globalSettings: _*)
  }
}

class SharedDepsExtender(settings: GlobalSettings) extends Extender {
  override def extend(p: Project) = {
    p.settings(libraryDependencies ++= settings.sharedDeps.toSeq)
  }
}

class GlobalExclusionsExtender(settings: GlobalSettings) extends Extender {
  override def extend(p: Project) = {
    p.settings(excludeDependencies ++= settings.globalExclusions.toSeq)
  }
}

class SharedModulesExtender(sharedLibs: Set[ProjectReferenceEx]) extends Extender {
  override def extend(p: Project) = {
    import IzumiScopes._

    if (!sharedLibs.contains(p)) {
      logger.debug(s"Adding ${sharedLibs} into $p")
      p.depends(sharedLibs.toSeq: _*)
    } else {
      p
    }
  }
}
