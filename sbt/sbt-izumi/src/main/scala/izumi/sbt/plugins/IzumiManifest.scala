package izumi.sbt.plugins

import java.time.format.DateTimeFormatter

// TODO: this is a copypaste from fundamentals, we need to find a way to avoid it
object IzumiManifest {
  val GitBranch = "X-Git-Branch"
  val GitRepoIsClean = "X-Git-Repo-Is-Clean"
  val GitHeadRev = "X-Git-Head-Rev"
  val BuiltBy = "X-Built-By"
  val BuildJdk = "X-Build-JDK"
  val BuildSbt = "X-Build-SBT"
  val BuildScala = "X-Build-Scala"
  val Version = "X-Version"
  val BuildTimestamp = "X-Build-Timestamp"
  val TsFormat: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
}
