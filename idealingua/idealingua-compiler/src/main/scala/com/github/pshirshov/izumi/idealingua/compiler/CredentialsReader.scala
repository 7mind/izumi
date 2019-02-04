package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage
import io.circe.parser.parse
import io.circe.generic.auto._

sealed trait Credentials {
  def lang: IDLLanguage
}
class LangCredentials(override val lang: IDLLanguage) extends Credentials
case class ScalaCredentials(realm: String, host: String, user: String, password: String,
                            releasesRepo: String, snapshotsRepo: String) extends LangCredentials(IDLLanguage.Scala)
case class TypescriptCredentials() extends LangCredentials(IDLLanguage.Typescript)
case class GoCredentials() extends LangCredentials(IDLLanguage.Go)
case class CsharpCredentials() extends LangCredentials(IDLLanguage.CSharp)

class CredentialsReader(lang: IDLLanguage, file: File) {
  def read(): Either[Throwable, Credentials] = lang match {
    case IDLLanguage.Scala => readScala(file)
    case IDLLanguage.Typescript => ???
    case IDLLanguage.Go => ???
    case IDLLanguage.CSharp => ???
  }

  def readScala(file: File): Either[Throwable, Credentials] = for {
    json <- parse(IzFiles.readString(file))
    creds <- json.as[ScalaCredentials]
  } yield creds.asInstanceOf[Credentials]
}
