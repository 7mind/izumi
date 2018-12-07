package com.github.pshirshov.izumi.idealingua.model

import com.github.pshirshov.izumi.idealingua.model
import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, Package, PackageTools}

import scala.reflect.{ClassTag, classTag}

final case class JavaType(pkg: Package, name: String, parameters: Seq[String] = Seq.empty) {
  def parent: JavaType = {
    JavaType(pkg.init, pkg.last, Seq.empty)
  }

  def withRoot: JavaType = {
    JavaType("_root_" +: pkg, name, parameters)
  }


  def minimize(domainId: DomainId): JavaType = {
    val minimalPackageRef = PackageTools.minimize(pkg, domainId.toPackage)
    JavaType(minimalPackageRef, name, parameters)
  }
}

object JavaType {
  def get[T: ClassTag]: JavaType = {
    val clazz = classTag[T].runtimeClass.getName
    assert(clazz.nonEmpty)
    val parts = clazz.split('.').toSeq
    model.JavaType(parts.init, parts.last, Seq.empty)
  }

  def apply(typeId: DomainId): JavaType = new JavaType(typeId.pkg, typeId.id, Seq.empty)
}
