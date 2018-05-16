package com.github.pshirshov.izumi.distage.config

import java.io.File
import java.math.BigInteger
import java.net.{URI, URL}
import java.time._
import java.util.UUID
import java.util.regex.Pattern

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.{ReflectionProvider, SymbolIntrospector}
import com.typesafe.config._

import scala.collection.JavaConverters._
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

  override def read(config: ConfigValue, tpe: TypeFull): Any = {
    anyReader(tpe)(config).get
  }

  def anyReader(tpe: TypeFull): ConfigReader[_] = {
    val safeType = SafeType(tpe.tpe.dealias.erasure)

    primitiveReaders.get(safeType) match {
      case Some(primitiveValueReader) =>
        primitiveValueReader
      case _ if tpe.tpe.erasure.baseClasses.contains(SafeType.get[Enum[_]].tpe.erasure.typeSymbol) =>
        val clazz = mirror.runtimeClass(tpe.tpe)

        ConfigReaderInstances.javaEnumReader(ClassTag(clazz))
      case _ if tpe <:< SafeType.get[scala.collection.immutable.Map[String, Any]] =>
        objectMapReader(tpe.tpe)
      // FIXME use collection's scala.collection.GenericCompanion to instantiate any collection
      case _ if tpe.tpe.dealias.erasure =:= u.typeOf[List[_]].dealias.erasure =>
        listReader(tpe.tpe.dealias, _.toList)
      case _ if tpe.tpe.dealias.erasure =:= u.typeOf[Seq[_]].dealias.erasure =>
        listReader(tpe.tpe.dealias, _.toSeq)
      case _ if tpe.tpe.dealias.erasure =:= u.typeOf[Set[_]].dealias.erasure =>
        listReader(tpe.tpe.dealias, _.toSet)
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
            p => (p.name, p.tpe)
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

  def objectMapReader(mapType: TypeNative): ConfigReader[Map[String, _]] = {
    case co: ConfigObject => Try {
      val tyParam = SafeType(mapType.typeArgs(1))
      co.asScala.toMap.mapValues {
        value =>
          anyReader(tyParam)(value).get
      }
    }
    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a map $mapType, config value is not an object.
           | ConfigValue was: $cv""".stripMargin))
  }

  def listReader[R](listType: TypeNative, ctor: Seq[_] => R): ConfigReader[R] = {
    case cl: ConfigList => Try {
      val tyParam = SafeType(listType.typeArgs.head)
      ctor.apply(cl.asScala.map {
        value =>
          anyReader(tyParam)(value).get
      })
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
