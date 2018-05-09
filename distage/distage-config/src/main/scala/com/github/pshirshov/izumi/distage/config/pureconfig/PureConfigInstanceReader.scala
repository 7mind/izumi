package com.github.pshirshov.izumi.distage.config.pureconfig

import com.github.pshirshov.izumi.distage.config.ConfigInstanceReader
import com.typesafe.config.Config


object PureConfigInstanceReader extends ConfigInstanceReader {
  override def read(value: Config, clazz: Class[_]): Product = {
    //    val companion = clazz.getClassLoader.loadClass(clazz.getCanonicalName + "PureConfigCodec")
    //    companion.asInstanceOf[WithPureConfig[Product]].read(value)

    ReflectionSugars.companion[WithPureConfig[Product]](clazz).read(value)
  }
}

trait ReflectionSugars {

  import scala.reflect.api._
  import scala.reflect.runtime.{universe => ru}

  private lazy val universeMirror = ru.runtimeMirror(getClass.getClassLoader)

  def companionOf[T](implicit tt: ru.TypeTag[T]): Any = {
    val companionModule = ru.typeOf[T].typeSymbol.companion.asModule
    val companionMirror = universeMirror.reflectModule(companionModule)
    companionMirror.instance
  }

  def companion[T](clazz: Class[_]): T = {
    val mirror = ru.runtimeMirror(clazz.getClassLoader)
    val selfType = mirror.classSymbol(clazz).selfType
    companionOf(typeToTypeTag(selfType, mirror)).asInstanceOf[T]
  }

  def typeToTypeTag[T](tpe: ru.Type, mirror: Mirror[ru.type]): ru.TypeTag[T] = {
    val tc = new TypeCreator {
      def apply[U <: Universe with Singleton](m: Mirror[U]): U#Type = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    }

    ru.TypeTag(mirror, tc)
  }
}

object ReflectionSugars extends ReflectionSugars {

}
