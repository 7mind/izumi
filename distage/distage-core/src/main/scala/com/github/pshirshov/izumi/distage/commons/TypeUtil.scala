package com.github.pshirshov.izumi.distage.commons

object TypeUtil {
  def isAssignableFrom(tpe: Class[_], obj: AnyRef): Boolean = {
    if (tpe == classOf[Integer] || tpe == classOf[Int]) {
      obj.getClass == classOf[Integer] || tpe == classOf[Int]
    } else if (tpe == classOf[Float] || tpe == classOf[Float]) {
      obj.getClass == classOf[Float] || tpe == classOf[Float]
    } else if (tpe == classOf[Double] || tpe == classOf[Double]) {
      obj.getClass == classOf[Double] || tpe == classOf[Double]
    } else if (tpe == classOf[Character] || tpe == classOf[Char]) {
      obj.getClass == classOf[Character] || tpe == classOf[Char]
    } else if (tpe == classOf[Long] || tpe == classOf[Long]) {
      obj.getClass == classOf[Long] || tpe == classOf[Long]
    } else if (tpe == classOf[Short] || tpe == classOf[Short]) {
      obj.getClass == classOf[Short] || tpe == classOf[Short]
    } else if (tpe == classOf[Boolean] || tpe == classOf[Boolean]) {
      obj.getClass == classOf[Boolean] || tpe == classOf[Boolean]
    } else if (tpe == classOf[Byte] || tpe == classOf[Byte]) {
      obj.getClass == classOf[Byte] || tpe == classOf[Byte]
    } else {
      tpe.isAssignableFrom(obj.getClass)
    }
  }

}
