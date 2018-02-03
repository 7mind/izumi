package com.github.pshirshov.izumi.idealingua.model.common

import scala.reflect._

case class JavaType(pkg: Package, name: String)

object JavaType {
  def get[T:ClassTag]: JavaType = {
    val clazz = classTag[T].runtimeClass
    JavaType(clazz.getPackage.getName.split('.'), clazz.getSimpleName)
  }
}