package izumi.fundamentals.platform.os

import java.io.File
import java.util.regex.Pattern

sealed trait OsType

object OsType {
  sealed trait Nix { this: OsType => }

  case object Mac extends OsType with Nix

  case object Linux extends OsType with Nix

  case object Windows extends OsType

  case object Unknown extends OsType

}

object IzOs {
  def path: Seq[String] = {
    Option(System.getenv("PATH"))
      .map(_.split(Pattern.quote(File.pathSeparator)).toSeq)
      .toSeq
      .flatten
  }

  def osType: OsType = {
    System.getProperty("os.name").toLowerCase match {
      case s if s.contains("windows") =>
        OsType.Windows
      case s if s.contains("darwin") || s.contains("mac") =>
        OsType.Mac
      case s if s.contains("linux") =>
        OsType.Linux
      case _ =>
        OsType.Unknown
    }
  }
}
