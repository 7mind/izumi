package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.jar

import scala.util.Try

case class GitStatus(branch: Option[String], repoClean: Boolean, revision: String)

case class AppVersion(version: String, buildBy: String, buildJdk: String, buildTimestamp: ZonedDateTime)

object IzumiManifest {
  val GitBranch = "X-Git-Branch"
  val GitRepoIsClean = "X-Git-Repo-Is-Clean"
  val GitHeadRev = "X-Git-Head-Rev"
  val BuiltBy = "X-Built-By"
  val BuildJdk = "X-Build-JDK"
  val Version = "X-Version"
  val BuildTimestamp = "X-Build-Timestamp"
  val TsFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  def manifest(): Option[jar.Manifest] = {
    IzResources.read("META-INF/MANIFEST.MF").map {
      is =>
        new java.util.jar.Manifest(is)
    }
  }

  def gitStatus(mf: jar.Manifest): Option[GitStatus] =
     Try {
       GitStatus(
        Option(mf.getMainAttributes.getValue(GitBranch))
        , Try(mf.getMainAttributes.getValue(GitRepoIsClean).toBoolean).toOption.getOrElse(false)
        , mf.getMainAttributes.getValue(GitHeadRev)
      )
    }.toOption

  def appVersion(mf: jar.Manifest): Option[AppVersion] =
    Try {
      AppVersion(
        mf.getMainAttributes.getValue(Version)
        , mf.getMainAttributes.getValue(BuiltBy)
        , mf.getMainAttributes.getValue(BuildJdk)
        , ZonedDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(mf.getMainAttributes.getValue(BuildTimestamp)))
      )
    }.toOption
}
