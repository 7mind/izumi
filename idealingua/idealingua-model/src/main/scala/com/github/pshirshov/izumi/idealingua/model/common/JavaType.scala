package com.github.pshirshov.izumi.idealingua.model.common

import scala.reflect._

case class JavaType(pkg: Package, name: String)

object JavaType {
  def get[T:ClassTag]: JavaType = {
    val clazz = classTag[T].runtimeClass
    val nameParts = clazz.getCanonicalName.split('$')
    JavaType(nameParts.init, nameParts.last)
  }

  def apply(typeId: TypeId): JavaType = new JavaType(typeId.pkg, typeId.name)
}
