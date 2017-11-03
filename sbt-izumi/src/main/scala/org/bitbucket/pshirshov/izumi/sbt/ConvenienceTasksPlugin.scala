package org.bitbucket.pshirshov.izumi.sbt

import sbt.Keys.version
import sbt._
import complete.DefaultParsers._
import sbt.internal.util.complete.Parser.token
import sbtrelease.Version

object ConvenienceTasksPlugin  extends AutoPlugin {
  object Keys {
    val addVersionSuffix = inputKey[Unit]("Add a suffix into version defined in version file")
  }

  import Keys._

  override def projectSettings = Seq(
    addVersionSuffix := {
      val suffix: String = (token(Space) ~> token(StringBasic, "name"))
        .parsed
        .replace('/', '_')
        .replace('.', '_')
        .replace('-', '_')

      val existingVersion= Version(version.value).get
      val newVersion = existingVersion
        .withoutQualifier
        .copy(qualifier = Some(s"-$suffix-SNAPSHOT"))
        .string

      IO.write(file("version.sbt"), s"""version in ThisBuild := "$newVersion"""")
    }
  )
}
