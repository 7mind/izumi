package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{Package, PackageTools, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il

import scala.reflect._

case class JavaType(pkg: Package, name: String) {
  def parent: JavaType = {
    JavaType(pkg.init, pkg.last)
  }

  def withRoot: JavaType = {
    JavaType("_root_" +: pkg, name)
  }


  def minimize(domainId: DomainId): JavaType = {
    val minimalPackageRef = PackageTools.minimize(pkg, domainId.toPackage)
    JavaType(minimalPackageRef, name)
  }
}

object JavaType {
  def get[T:ClassTag]: JavaType = {
    val clazz = classTag[T].runtimeClass.getCanonicalName

    val parts = clazz.split('.').toSeq
    val nameParts = parts.last.split('$').toSeq
    val pkg = parts.init ++ nameParts.init
    il.JavaType(pkg, nameParts.last)
  }

  def apply(typeId: TypeId): JavaType = new JavaType(typeId.pkg, typeId.name)
  def apply(typeId: DomainId): JavaType = new JavaType(typeId.pkg, typeId.id)
}
