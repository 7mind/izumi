package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.configapp.TestConfigApp
import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.typesafe.config._
import org.scalatest.WordSpec
import pureconfig._
import shapeless.{Cached, Lazy}



abstract class WithPureConfig[T]() {
  type R[X] = Derivation[ConfigReader[X]]
  def reader: WithPureConfig.R[T]


  def read(configValue: Config): T = {
    implicit def r: R[T] = reader
    loadConfig[T](configValue) match {
      case Right(v) =>
        v
      case Left(e) =>
        throw new DIException(s"Can't read config: $e", null)
    }
  }
}

object WithPureConfig {
  type R[X] = Derivation[ConfigReader[X]]
  type C[T] = Cached[Lazy[R[T]]]
}

object PureConfigInstanceReader extends ConfigInstanceReader {
  override def read(value: Config, clazz: Class[_]): Product = {
    import ReflectionSugars._
    companion(clazz).asInstanceOf[WithPureConfig[Product]].read(value)
  }
}

trait ReflectionSugars{
  import scala.reflect.runtime.{universe => ru}
  private lazy val universeMirror = ru.runtimeMirror(getClass.getClassLoader)

  def companionOf[T](implicit tt: ru.TypeTag[T])  = {
    val companionMirror = universeMirror.reflectModule(ru.typeOf[T].typeSymbol.companion.asModule)
    companionMirror.instance
  }

  def companion(clazz: Class[_])  = {
    val m = ru.runtimeMirror(clazz.getClassLoader)
    val selfType = m.classSymbol(clazz).selfType
    companionOf(typeToTypeTag(selfType, m))
  }

  def typeToTypeTag[T](
                        tpe: ru.Type,
                        mirror: reflect.api.Mirror[reflect.runtime.universe.type]
                      ): ru.TypeTag[T] = {
    ru.TypeTag(mirror, new reflect.api.TypeCreator {
      def apply[U <: reflect.api.Universe with Singleton](m: reflect.api.Mirror[U]) = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    })
  }
}

object ReflectionSugars extends ReflectionSugars {

}

//object DummyConfigInstanceReader extends ConfigInstanceReader {
//  def read(value: Config, clazz: Class[_]): Product = {
//    clazz.getConstructor(classOf[Int], classOf[String]).newInstance(0.intValue().underlying(), "").asInstanceOf[Product]
//  }
//}

class ConfigTest extends WordSpec {
  "Config resolver" should {
    "resolve config references" in {
      val config = AppConfig(ConfigFactory.load())
      val injector = Injectors.bootstrap(new ConfigModule(config, PureConfigInstanceReader))
      val plan = injector.plan(TestConfigApp.definition)
      //println(plan)

      val context = injector.produce(plan)
      assert(context.get[TestConfigApp].services.size == 3)
    }
  }

}

