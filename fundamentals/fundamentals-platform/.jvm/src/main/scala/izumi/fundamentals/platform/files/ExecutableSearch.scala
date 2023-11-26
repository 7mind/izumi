package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.os.{IzOs, OsType}

import java.nio.file.{Path, Paths}

trait ExecutableSearch {
  def haveExecutables(names: String*): Boolean = {
    names.forall(which(_).nonEmpty)
  }

  def which(name: String, morePaths: Seq[String] = Seq.empty): Option[Path] = {
    find(binaryNameCandidates(name), IzOs.path ++ morePaths)
  }

  def whichAll(name: String, morePaths: Seq[String] = Seq.empty): Iterable[Path] = {
    findAll(binaryNameCandidates(name), IzOs.path ++ morePaths)
  }

  private def find(candidates: Seq[String], paths: Seq[String]): Option[Path] = {
    paths.view
      .flatMap {
        p =>
          candidates.map(ext => Paths.get(p).resolve(ext))
      }
      .find {
        p =>
          p.toFile.exists()
      }
  }

  private def findAll(candidates: Seq[String], paths: Seq[String]): Iterable[Path] = {
    paths.view
      .flatMap {
        p =>
          candidates.map(ext => Paths.get(p).resolve(ext))
      }
      .filter {
        p =>
          p.toFile.exists()
      }
  }

  private def binaryNameCandidates(name: String): Seq[String] = {
    IzOs.osType match {
      case OsType.Windows =>
        Seq("exe", "com", "bat").map(ext => s"$name.$ext")
      case _ =>
        Seq(name)
    }
  }
}
