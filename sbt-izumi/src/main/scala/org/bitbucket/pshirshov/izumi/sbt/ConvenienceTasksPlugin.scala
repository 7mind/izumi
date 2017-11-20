package org.bitbucket.pshirshov.izumi.sbt

import sbt.Keys.version
import sbt._
import complete.DefaultParsers._
import sbt.internal.util.ConsoleLogger
import sbt.internal.util.complete.Parser.token
import sbtrelease.Version

object ConvenienceTasksPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  object Keys {
    val addVersionSuffix = inputKey[Unit]("Add a suffix into version defined in version file")
    val preserveTargets = inputKey[Unit]("Preserve 'target' directories")
    val rmDirs = inputKey[Unit]("Recursively remove directories with a name provided")
  }

  import Keys._

  override def projectSettings = Seq(
    addVersionSuffix := {
      val suffix: String = (token(Space) ~> token(StringBasic, "name"))
        .parsed
        .replace('/', '_')
        .replace('.', '_')
        .replace('-', '_')

      val existingVersion = Version(version.value).get
      val newVersion = existingVersion
        .withoutQualifier
        .copy(qualifier = Some(s"-$suffix-SNAPSHOT"))
        .string

      IO.write(file("version.sbt"), s"""version in ThisBuild := "$newVersion"""")
    }
    , preserveTargets := {
      val suffix: String = (token(Space) ~> token(StringBasic, "name"))
        .parsed

      val name = "target"
      val pairs = (file(".") ** (DirectoryFilter && new ExactFilter(name))).get.map {
        fn =>
          val withSuffix = fn.toPath.getParent.resolve(s"${fn.getName}.$suffix")
          logger.debug(s"Preserving directory $fn => $withSuffix")
          fn -> withSuffix.toFile
      }
      IO.delete(pairs.map(_._2))
      pairs.foreach {
        case (s, t) =>
          IO.copyDirectory(s, t)
      }
    }
    , rmDirs := {
      val name: String = (token(Space) ~> token(StringBasic, "name"))
        .parsed

      val dirs = (file(".") ** (DirectoryFilter && new ExactFilter(name))).get
      IO.delete(dirs)
    }
  )
}
