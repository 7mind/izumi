package com.github.pshirshov.izumi.distage.config.codec

import java.io.File
import java.math.BigInteger
import java.net.{URI, URL}
import java.time._
import java.util.UUID
import java.util.regex.Pattern

import com.github.pshirshov.izumi.distage.config.model.ConfigReadException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.{ReflectionProvider, SymbolIntrospector}
import com.typesafe.config._

import scala.collection.JavaConverters._
import scala.collection.generic.GenMapFactory
import scala.collection.{GenMap, GenTraversable}
import scala.reflect.ClassTag
import scala.reflect.io.Path
import scala.util.matching.Regex
import scala.util.{Failure, Try}

class RuntimeConfigReaderDefaultImpl
(
  reflectionProviderDefaultImpl: ReflectionProvider.Runtime
  , symbolIntrospector: SymbolIntrospector.Runtime
) extends RuntimeConfigReader {

  import RuntimeConfigReaderDefaultImpl._

  override def readConfig(config: Config, tpe: TypeFull): Any = {
    deriveCaseClassReader(tpe)(config.root()).get
  }

  def anyReader(tpe: TypeFull): ConfigReader[_] = {
    val safeType = SafeType(tpe.tpe.dealias.erasure)

    primitiveReaders.get(safeType) match {
      case Some(primitiveValueReader) =>
        primitiveValueReader
      case _ if tpe.tpe.erasure.baseClasses.contains(SafeType.get[Enum[_]].tpe.erasure.typeSymbol) =>
        val clazz = mirror.runtimeClass(tpe.tpe)

        ConfigReaderInstances.javaEnumReader(ClassTag(clazz))
      case _ if tpe <:< SafeType.get[GenMap[String, Any]] =>
        objectMapReader(tpe.tpe.dealias)
      case _ if tpe <:< SafeType.get[GenTraversable[Any]] =>
        listReader(tpe.tpe.dealias)
      case _ if tpe.tpe.dealias.erasure =:= u.typeOf[Option[_]].dealias.erasure =>
        optionReader(tpe.tpe.dealias)
      case _ =>
        deriveCaseClassReader(tpe)
    }
  }

  def deriveCaseClassReader(targetType: TypeFull): ConfigReader[_] =
    cv => Try {
      if(!symbolIntrospector.isConcrete(targetType)) {
        throw new ConfigReadException(
          s"""Only case classes can be read in config, but type $targetType is not concrete: it's a trait or abstract class.
             | When trying to derive case class reader for $targetType""".stripMargin)
      }

      cv match {
        case obj: ConfigObject =>
          val params = reflectionProviderDefaultImpl.constructorParameters(targetType).map {
            p => p.name -> p.tpe
          }
          val constructorSymbol = symbolIntrospector.selectConstructorMethod(targetType)

          val parsedArgs: List[_] = params.map {
            case (name, tpe) =>
              val value = obj.get(name)
              anyReader(tpe)(value).get
          }

          val reflectedClass = mirror.reflectClass(targetType.tpe.typeSymbol.asClass)
          val constructor = reflectedClass.reflectConstructor(constructorSymbol)

          constructor.apply(parsedArgs: _*)
        case _ =>
          throw new ConfigReadException(
            s"""
               |Can't read config value as case class $targetType, config value is not an object.
               | ConfigValue was: $cv""".stripMargin)
      }
    }

  def objectMapReader(mapType: TypeNative): ConfigReader[GenMap[String, _]] = {
    case co: ConfigObject => Try {
      val tyParam = SafeType(mapType.dealias.typeArgs.last)
      mirror.reflectModule(mapType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
        case companionFactory: GenMapFactory[GenMap] @unchecked =>
          val kvs = co.asScala.toMap.mapValues(anyReader(tyParam)(_).get)
          companionFactory.apply(kvs.toSeq: _*)
        case c =>
          throw new ConfigReadException(
            s"""When trying to read a Map type $mapType: can't instantiate class. Expected a companion object of type
               | scala.collection.generic.GenMapFactory to be present, but $mapType companion object has type: ${c.getClass}.
               |
               | ConfigValue was: $co""".stripMargin)
      }
    }
    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a map $mapType, config value is not an object.
           | ConfigValue was: $cv""".stripMargin))
  }

  def listReader(listType: TypeNative): ConfigReader[GenTraversable[_]] = {
    case cl: ConfigList => Try {
      val tyParam = SafeType(listType.dealias.typeArgs.last)

      mirror.reflectModule(listType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
        case companionFactory: scala.collection.generic.GenericCompanion[GenTraversable] @unchecked =>
          val values: Seq[_] = cl.asScala.map(anyReader(tyParam)(_).get)
          companionFactory(values: _*)
        case c =>
          throw new ConfigReadException(
            s"""When trying to read a collection type $listType: can't instantiate class. Expected a companion object of type
               | scala.collection.generic.GenericCompanion to be present, but $listType companion object has type: ${c.getClass}.
               |
               | ConfigValue was: $cl""".stripMargin)
      }
    }
    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a list $listType, config value is not a list.
           | ConfigValue was: $cv""".stripMargin))
  }

  def optionReader(optionType: TypeNative): ConfigReader[Option[_]] = {
    cv => Try {
      if (cv.valueType == ConfigValueType.NULL) {
        None
      } else {
        val tyParam = SafeType(optionType.typeArgs.head)
        Option(anyReader(tyParam)(cv).get)
      }
    }
  }
}

object RuntimeConfigReaderDefaultImpl {
  import ConfigReaderInstances._

  val primitiveReaders: Map[TypeFull, ConfigReader[_]] = Map(
      SafeType.get[String] -> stringConfigReader
    , SafeType.get[Char] -> charConfigReader
    , SafeType.get[Boolean] -> booleanConfigReader
    , SafeType.get[Double] -> doubleConfigReader
    , SafeType.get[Float] -> floatConfigReader
    , SafeType.get[Int] -> intConfigReader
    , SafeType.get[Long] -> longConfigReader
    , SafeType.get[Short] -> shortConfigReader

    , SafeType.get[BigInteger] -> javaBigIntegerReader
    , SafeType.get[java.math.BigDecimal] -> javaBigDecimalReader
    , SafeType.get[BigInt] -> scalaBigIntReader
    , SafeType.get[BigDecimal] -> scalaBigDecimalReader

    , SafeType.get[Instant] -> instantConfigReader
    , SafeType.get[ZoneOffset] -> zoneOffsetConfigReader
    , SafeType.get[ZoneId] -> zoneIdConfigReader
    , SafeType.get[Period] -> periodConfigReader
    , SafeType.get[java.time.Duration] -> javaDurationConfigReader
    , SafeType.get[Year] -> yearConfigReader

    , SafeType.get[URL] -> urlConfigReader
    , SafeType.get[URI] -> uriConfigReader
    , SafeType.get[UUID] -> uuidConfigReader
    , SafeType.get[Path] -> pathConfigReader
    , SafeType.get[File] -> fileConfigReader

    , SafeType.get[Pattern] -> patternReader
    , SafeType.get[Regex] -> regexReader

    , SafeType.get[Config] -> configConfigReader
    , SafeType.get[ConfigObject] -> configObjectConfigReader
    , SafeType.get[ConfigValue] -> configValueConfigReader
    , SafeType.get[ConfigList] -> configListConfigReader
  )

}
