package izumi.idealingua.translator.toscala.types.runtime

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.translator.toscala.types.ScalaTypeConverter

import scala.reflect.{ClassTag, classTag}


final case class Pkg private(pkgParts: Seq[String]) {
  final val conv = new ScalaTypeConverter(DomainId(pkgParts.init, pkgParts.last))

  def within(name: String) = Pkg(pkgParts :+ name)

  def `import`: Import = Import.AllPackage(this)
}

object Pkg {

  def of[T: ClassTag]: Pkg = {
    val className = classTag[T].runtimeClass.getName
    val classPkgParts = className.split('.').init
    Pkg(classPkgParts)
  }

  def language: Pkg = Pkg(Seq("scala", "language"))
}
