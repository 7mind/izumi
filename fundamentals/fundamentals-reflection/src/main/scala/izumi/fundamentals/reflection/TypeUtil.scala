package izumi.fundamentals.reflection

import java.lang.reflect.{Constructor, Field}

object TypeUtil {
  def isAssignableFrom(superClass: Class[_], obj: Any): Boolean = {
    def instanceClass = obj.getClass

    if (obj == null) {
      true
    } else if (superClass == classOf[java.lang.Integer] || superClass == classOf[Int]) {
      instanceClass == classOf[java.lang.Integer] || instanceClass == classOf[Int]
    } else if (superClass == classOf[java.lang.Float] || superClass == classOf[Float]) {
      instanceClass == classOf[java.lang.Float] || instanceClass == classOf[Float]
    } else if (superClass == classOf[java.lang.Double] || superClass == classOf[Double]) {
      instanceClass == classOf[java.lang.Double] || instanceClass == classOf[Double]
    } else if (superClass == classOf[java.lang.Character] || superClass == classOf[Char]) {
      instanceClass == classOf[java.lang.Character] || instanceClass == classOf[Char]
    } else if (superClass == classOf[java.lang.Long] || superClass == classOf[Long]) {
      instanceClass == classOf[java.lang.Long] || instanceClass == classOf[Long]
    } else if (superClass == classOf[java.lang.Short] || superClass == classOf[Short]) {
      instanceClass == classOf[java.lang.Short] || instanceClass == classOf[Short]
    } else if (superClass == classOf[java.lang.Boolean] || superClass == classOf[Boolean]) {
      instanceClass == classOf[java.lang.Boolean] || instanceClass == classOf[Boolean]
    } else if (superClass == classOf[java.lang.Byte] || superClass == classOf[Byte]) {
      instanceClass == classOf[java.lang.Byte] || instanceClass == classOf[Byte]
    } else {
      superClass.isAssignableFrom(instanceClass)
    }
  }

  final def defaultValue(clazz: Class[_]): Any = {
    if (clazz == classOf[java.lang.Integer] || clazz == classOf[Int]) {
      0: Int
    } else if (clazz == classOf[java.lang.Float] || clazz == classOf[Float]) {
      0.0f: Float
    } else if (clazz == classOf[java.lang.Double] || clazz == classOf[Double]) {
      0.0d: Double
    } else if (clazz == classOf[java.lang.Character] || clazz == classOf[Char]) {
      '\u0000': Char
    } else if (clazz == classOf[java.lang.Long] || clazz == classOf[Long]) {
      0L: Long
    } else if (clazz == classOf[java.lang.Short] || clazz == classOf[Short]) {
      0: Short
    } else if (clazz == classOf[java.lang.Boolean] || clazz == classOf[Boolean]) {
      false: Boolean
    } else if (clazz == classOf[java.lang.Byte] || clazz == classOf[Byte]) {
      0: Byte
    } else {
      null: Any
    }
  }

  final def isObject(clazz: Class[_]): Option[Field] = {
    val name = clazz.getName
    if ((name ne null) && name.endsWith("$")) {
      try {
        val field = clazz.getField("MODULE$")
        if ((field.getModifiers & java.lang.reflect.Modifier.STATIC) != 0) Some(field) else None
      } catch {
        case _: NoSuchFieldException => None
      }
    } else {
      None
    }
  }

  final def isZeroArgClass(clazz: Class[_]): Option[Constructor[_]] = {
    clazz.getDeclaredConstructors.find(_.getParameterCount == 0)
  }

  @inline final def instantiateObject[T](clazz: Class[_]): T = {
    clazz.getField("MODULE$").get(null).asInstanceOf[T]
  }

  @inline final def instantiateObject[T](field: Field): T = {
    field.get(null).asInstanceOf[T]
  }

  @inline final def instantiateZeroArgClass[T](clazz: Class[_]): Option[T] = {
    isZeroArgClass(clazz).map(instantiateZeroArgClass[T])
  }

  @inline final def instantiateZeroArgClass[T](ctor: Constructor[_]): T = {
    ctor.newInstance().asInstanceOf[T]
  }

  final def isAnonymous(clazz: Class[_]): Boolean = {
    clazz.isAnonymousClass || clazz.getName.contains("$anon$")
  }

}
