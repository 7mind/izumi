package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file._
import java.time.ZonedDateTime
import java.util.Base64

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.GoLangBuildManifest
import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage

import scala.sys.process.Process
import scala.util.Try
import scala.collection.JavaConverters._


class ArtifactPublisher(targetDir: Path, lang: IDLLanguage, creds: Credentials, manifest: BuildManifest) {
  private val log: CompilerLog = CompilerLog.Default

  def publish(): Either[Throwable, Unit] = (creds, lang, manifest) match {
    case (c: ScalaCredentials, IDLLanguage.Scala, _) => publishScala(targetDir, c)
    case (c: TypescriptCredentials, IDLLanguage.Typescript, _) => publishTypescript(targetDir, c)
    case (c: GoCredentials, IDLLanguage.Go, m: GoLangBuildManifest) => publishGo(targetDir, c, m)
    case (c: CsharpCredentials, IDLLanguage.CSharp, _) => publishCsharp(targetDir, c)
    case (c, l, _) if c.lang != l =>
      Left(new IllegalArgumentException("Language and credentials type didn't match. " +
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
      // TODO: Gigahorse apears to be cause of `Too many follow-up requests: 21` exception during publishing
      "updateOptions in ThisBuild := updateOptions.value.withGigahorse(false)",
      "\n",
      s"""credentials += Credentials(Path("${sbtCredsFile.toAbsolutePath.toString}").asFile)""",
      "\n",
      s"""
        |publishTo in ThisBuild := {
        |  if (isSnapshot.value)
        |    Some("snapshots" at "${creds.sbtSnapshotsRepo}")
        |  else
        |    Some("releases"  at "${creds.sbtReleasesRepo}")
        |}
      """.stripMargin
    )

    Files.write(buildFile, credsLines.asJava, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    Files.write(sbtCredsFile, Seq[String](
      s"realm=${creds.sbtRealm}",
      s"host=${creds.sbtHost}",
      s"user=${creds.sbtUser}",
      s"password=${creds.sbtPassword}"
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
    val auth = Base64.getEncoder.encode(creds.npmPassword.getBytes)
    val repoName = creds.npmRepo.replaceAll("http://", "").replaceAll("https://", "")
    log.log(s"Writing credentials in ${credsFile.toAbsolutePath.getFileName}")
    Process(
      s"npm config set registry ${creds.npmRepo}"
    )
    Files.write(Paths.get(System.getProperty("user.home")).resolve(".npmrc"), Seq(
      s"""
         |$scope:registry=${creds.npmRepo}
         |//$repoName:_password=$auth
         |//$repoName:username=${creds.npmUser}
         |//$repoName:email=${creds.npmEmail}
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

  private def publishCsharp(targetDir: Path, creds: CsharpCredentials): Either[Throwable, Unit] = Try {
    log.log("Prepare to package C# sources")

    log.log("Preparing credentials")
    Process(
      s"nuget sources Add -Name IzumiPublishSource -Source ${creds.nugetRepo}", targetDir.toFile
    ).#||("true").lineStream.foreach(log.log)

    Process(
      s"nuget setapikey ${creds.nugetUser}:${creds.nugetPassword} -Source IzumiPublishSource", targetDir.toFile
    ).lineStream.foreach(log.log)

    log.log("Publishing")
    Files.list(targetDir.resolve("nuspec")).filter(_.getFileName.toString.endsWith(".nuspec")).iterator().asScala.foreach { module =>
      Try(
        Process(
          s"nuget pack ${module.getFileName.toString}", targetDir.resolve("nuspec").toFile
        ).lineStream.foreach(log.log)
      )
    }

    IzFiles.walk(targetDir.resolve("nuspec").toFile).filter(_.getFileName.toString.endsWith(".nupkg")).foreach { pack =>
      Process(
        s"nuget push ${pack.getFileName.toString} -Source IzumiPublishSource", targetDir.resolve("nuspec").toFile
      ).lineStream.foreach(log.log)
    }
  }.toEither

  private def publishGo(targetDir: Path, creds: GoCredentials, manifest: GoLangBuildManifest): Either[Throwable, Unit] = Try {
    log.log("Prepare to package GoLang sources")

    val env = "GOPATH" -> s"${targetDir.toAbsolutePath.toString}"
    IzFiles.recreateDir(targetDir.resolve("src"))

    Files.move(targetDir.resolve("github.com"), targetDir.resolve("src/github.com"), StandardCopyOption.REPLACE_EXISTING)

    Process(
      "go get github.com/gorilla/websocket", targetDir.toFile, env
    ).lineStream.foreach(log.log)

    log.log("Testing")
    Process(
      "go test ./...", targetDir.resolve("src").toFile, env
    ).lineStream.foreach(log.log)
    log.log("Testing - OK")

    log.log("Publishing to github repo")

    log.log("Seting up Git")
    val pubKey = targetDir.resolve("go-key.pub")
    Files.write(pubKey, Seq(creds.gitPubKey).asJava)

    Process(
      s"""git config --global core.sshCommand "ssh -i ${pubKey.toAbsolutePath.toString} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no" """
    ).lineStream.foreach(log.log)
    Process(
      s"""git config --global user.name "${creds.gitUser}""""
    ).lineStream.foreach(log.log)
    Process(
      s"""git config --global user.email "${creds.gitEmail}""""
    ).lineStream.foreach(log.log)

    Process(
      s"git clone ${creds.gitRepoUrl}",targetDir.toFile
    ).lineStream.foreach(log.log)

    Files.list(targetDir.resolve(creds.gitRepoName)).iterator().asScala
      .filter(_.getFileName.toString.charAt(0) != '.')
      .foreach { path =>
        IzFiles.removeDir(path)
      }

    Files.list(targetDir.resolve("src").resolve(manifest.repository.repository)).iterator().asScala.foreach { srcDir =>
      Files.move(srcDir, targetDir.resolve(creds.gitRepoName).resolve(srcDir.getFileName.toString))
    }

    Files.write(targetDir.resolve(creds.gitRepoName).resolve(".timestamp"), Seq(
      ZonedDateTime.now().toString
    ).asJava)
    Files.write(targetDir.resolve(creds.gitRepoName).resolve("README.md"), Seq(
      s"# ${creds.gitRepoName}",
      s"Auto-generated golang apis, ${manifest.common.version.toString}"
    ).asJava)

    Process(
      "git add .", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
    Process(
      s"""git commit --no-edit -am "golang-api-update,version=${manifest.common.version}"""", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
    Process(
      s"git tag -f v${manifest.common.version.toString}}", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
    Process(
      "git push --all -f", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
    Process(
      "git push --tags -f", targetDir.resolve(creds.gitRepoName).toFile
    ).lineStream.foreach(log.log)
  }.toEither
}
