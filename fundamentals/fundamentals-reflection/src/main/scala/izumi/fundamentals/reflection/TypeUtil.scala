package izumi.fundamentals.reflection

object TypeUtil {
  def isAssignableFrom(tpe: Class[_], obj: Any): Boolean = {
    def instanceClass = obj.getClass

    if (obj == null) {
      true
    } else if (tpe == classOf[java.lang.Integer] || tpe == classOf[Int]) {
      instanceClass == classOf[java.lang.Integer] || instanceClass == classOf[Int]
    } else if (tpe == classOf[java.lang.Float] || tpe == classOf[Float]) {
      instanceClass == classOf[java.lang.Float] || instanceClass == classOf[Float]
    } else if (tpe == classOf[java.lang.Double] || instanceClass == classOf[Double]) {
      instanceClass == classOf[java.lang.Double] || instanceClass == classOf[Double]
    } else if (tpe == classOf[java.lang.Character] || tpe == classOf[Char]) {
      instanceClass == classOf[java.lang.Character] || instanceClass == classOf[Char]
    } else if (tpe == classOf[java.lang.Long] || tpe == classOf[Long]) {
      instanceClass == classOf[java.lang.Long] || instanceClass == classOf[Long]
    } else if (tpe == classOf[java.lang.Short] || tpe == classOf[Short]) {
      instanceClass == classOf[java.lang.Short] || instanceClass == classOf[Short]
    } else if (tpe == classOf[java.lang.Boolean] || tpe == classOf[Boolean]) {
      instanceClass == classOf[java.lang.Boolean] || instanceClass == classOf[Boolean]
    } else if (tpe == classOf[java.lang.Byte] || tpe == classOf[Byte]) {
      instanceClass == classOf[java.lang.Byte] || instanceClass == classOf[Byte]
    } else {
      tpe.isAssignableFrom(instanceClass)
    }
  }

}
