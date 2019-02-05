package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util.Base64

import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage

import scala.sys.process.Process
import scala.util.Try
import scala.collection.JavaConverters._


class ArtifactPublisher(path: Path, lang: IDLLanguage, creds: Credentials, langOpts: LanguageOpts) {
  private val log: CompilerLog = CompilerLog.Default

  def publish(): Either[Throwable, Unit] = (creds, lang) match {
    case (c: ScalaCredentials, IDLLanguage.Scala) => publishScala(path, c)
    case (c: TypescriptCredentials, IDLLanguage.Typescript) => publishTypescript(path, c)
    case (c: GoCredentials, IDLLanguage.Go) => ???
    case (c: CsharpCredentials, IDLLanguage.CSharp) => ???
    case (c, l) if c.lang != l =>
      Left(new IllegalArgumentException(s"Language and credentials type didn't match. " +
        s"Got credentials for $l, expect for ${c.lang}"))
  }

  private def publishScala(targetDir: Path, creds: ScalaCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package Scala sources")
    Process(
      "sbt clean package",
      targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Writing credentials file to ")
    val buildFile = targetDir.toAbsolutePath.resolve("build.sbt")
    val sbtCredsFile = targetDir.toAbsolutePath.resolve(".credentials")

    val credsLines = Seq(
      "\n",
      s"""credentials += Credentials(Path("${sbtCredsFile.toAbsolutePath.toString}").asFile)""",
      "\n",
      s"""
        |publishTo := {
        |  if (isSnapshot.value)
        |    Some("snapshots" at "${creds.snapshotsRepo}")
        |  else
        |    Some("releases"  at "${creds.releasesRepo}")
        |}
      """.stripMargin
    )

    Files.write(buildFile, credsLines.asJava, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    Files.write(sbtCredsFile, Seq[String](
      s"realm=${creds.realm}",
      s"host=${creds.host}",
      s"user=${creds.user}",
      s"password=${creds.password}"
    ).asJava)

    Process(
      "sbt publish",
      targetDir.toFile
    ).lineStream.foreach(log.log)
  }.toEither

  private def publishTypescript(targetDir: Path, creds: TypescriptCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package Typescript sources")

    log.log("Yarn installing")
    Process(
      "yarn install" ,
      targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Yarn building")
    Process(
      "yarn build" ,
      targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Publishing NPM packages")
    val packagesDir = Files.list(targetDir.resolve("packages")).filter(_.toFile.isDirectory).iterator().asScala.toSeq.head
    val scope = packagesDir.getFileName

    val credsFile = Paths.get(System.getProperty("user.home")).resolve("~/.npmrc")
    val auth = Base64.getEncoder.encode(creds.password.getBytes)
    val repoName = creds.npmRepo.replaceAll("http://", "").replaceAll("https://", "")
    log.log(s"Writing credentials in ${credsFile.toAbsolutePath.getFileName}")
    Process(
      s"npm config set registry ${creds.npmRepo}"
    )
    Files.write(Paths.get(System.getProperty("user.home")).resolve(".npmrc"), Seq(
      s"""
         |$scope:registry=${creds.npmRepo}
         |//$repoName:_password=$auth
         |//$repoName:username=${creds.user}
         |//$repoName:email=${creds.email}
         |//$repoName:always-auth=true
      """.stripMargin
    ).asJava)

    Files.list(targetDir.resolve("dist")).filter(_.toFile.isDirectory).iterator().asScala.foreach { module =>
      val cmd = s"npm publish --force --registry ${creds.npmRepo} ${module.toAbsolutePath.toString}"
      log.log(s"Publish ${module.getFileName}. Cmd: `$cmd`")
      Files.copy(packagesDir.resolve(s"${module.getFileName}/package.json"), module.resolve("package.json"))
      Process(cmd, targetDir.toFile).lineStream.foreach(log.log)
    }
  }.toEither
}
