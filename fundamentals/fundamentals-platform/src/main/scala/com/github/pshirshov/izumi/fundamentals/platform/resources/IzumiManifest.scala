package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.jar

import scala.util.Try


object IzumiManifest {
  val GitBranch = "X-Git-Branch"
  val GitRepoIsClean = "X-Git-Repo-Is-Clean"
  val GitHeadRev = "X-Git-Head-Rev"
  val BuiltBy = "X-Built-By"
  val BuildJdk = "X-Build-JDK"
  val Version = "X-Version"
  val BuildTimestamp = "X-Build-Timestamp"
  val TsFormat: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  val ManifestName = "META-INF/MANIFEST.MF"

  def read(): Option[ManifestData] = {
    read(ManifestName)
  }

  def manifest(): Option[jar.Manifest] = {
    manifest(ManifestName)
  }

  def read(name: String): Option[ManifestData] = {
    manifest(name).map {
      mf =>
        ManifestData(gitStatus(mf), appVersion(mf), appBuild(mf))
    }
  }

  def manifest(name: String): Option[jar.Manifest] = {
    IzResources
      .read(name)
      .map {
        is =>
          new java.util.jar.Manifest(is)
      }
  }

  def gitStatus(mf: jar.Manifest): Option[GitStatus] =
    Try {
      GitStatus(
        Option(mf.getMainAttributes.getValue(GitBranch))
        , Try(mf.getMainAttributes.getValue(GitRepoIsClean).toBoolean).toOption.getOrElse(false)
        , Option(mf.getMainAttributes.getValue(GitHeadRev))
      )
    }.toOption

  def appVersion(mf: jar.Manifest): Option[ArtifactVersion] =
    Try {
      ArtifactVersion(
        mf.getMainAttributes.getValue(Version)
      )
    }.toOption

  def appBuild(mf: jar.Manifest): Option[BuildStatus] =
    Try {
      BuildStatus(
        mf.getMainAttributes.getValue(BuiltBy)
        , mf.getMainAttributes.getValue(BuildJdk)
        , ZonedDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(mf.getMainAttributes.getValue(BuildTimestamp)))
      )
    }.toOption


}

