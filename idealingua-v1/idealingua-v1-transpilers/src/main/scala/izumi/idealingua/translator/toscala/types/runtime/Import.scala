package izumi.idealingua.translator.toscala.types.runtime

import scala.reflect._

trait Import {
  def render: String
}

object Import {

  case class AllPackage(pkg: Pkg) extends Import {
    override def render: String = {
      val parts = "_root_" +: pkg.pkgParts :+ "_"
      s"import ${parts.mkString(".")}"
    }
  }

  case class PackageMembers(pkg: Pkg, members: (String, Option[String])*) extends Import {
    override def render: String = {
      val end = if (members.size == 1 && members.head._2.isEmpty) {
        members.head._1
      } else {
        members
          .map {
            case (m, None) => m
            case (m, Some(n)) => s"$m => $n"
          }
          .mkString("{ ", ", ", " }")
      }

      val parts = "_root_" +: pkg.pkgParts :+ end
      s"import ${parts.mkString(".")}"
    }
  }

  def from(pkg: Pkg, symbol: String, name: Option[String] = None): Import = {
    PackageMembers(pkg, symbol -> name)
  }

  def apply[T: ClassTag](name: Option[String] = None): Import = {
    from(Pkg.of[T], classTag[T].runtimeClass.getSimpleName, name)
  }
}
