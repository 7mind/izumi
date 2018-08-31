package com.github.pshirshov.izumi.fundamentals.typesafe.config

import com.github.pshirshov.izumi.fundamentals.reflection.SafeType0
import com.typesafe.config._

import scala.collection.JavaConverters._
import scala.collection.generic.{GenMapFactory, GenericCompanion}
import scala.collection.{GenMap, GenTraversable}
import scala.reflect.ClassTag
import scala.reflect.runtime.{currentMirror, universe => ru}
import scala.util.{Failure, Try}

class RuntimeConfigReaderDefaultImpl
(
  val codecs: Map[SafeType0[ru.type], ConfigReader[_]]
) extends RuntimeConfigReader {

  val mirror: ru.Mirror = currentMirror

  override def readConfig(config: Config, tpe: SafeType0[ru.type]): Any = {
    deriveCaseClassReader(tpe)(config.root()).get
  }

  override def readValue(config: ConfigValue, tpe: SafeType0[ru.type]): Any = {
    anyReader(tpe)(config).get
  }

  def anyReader(tpe: SafeType0[ru.type]): ConfigReader[_] = {
    val key = SafeType0[ru.type](tpe.tpe.dealias.erasure)

    codecs.get(key) match {
      case Some(primitiveValueReader) =>
        primitiveValueReader
      case _ if tpe.tpe.erasure.baseClasses.contains(SafeType0[Enum[_]].tpe.erasure.typeSymbol) =>
        val clazz = mirror.runtimeClass(tpe.tpe)

        ConfigReaderInstances.javaEnumReader(ClassTag(clazz))
      case _ if tpe <:< SafeType0[GenMap[String, Any]] =>
        objectMapReader(tpe.tpe.dealias)
      case _ if tpe <:< SafeType0[GenTraversable[Any]] =>
        listReader(tpe.tpe.dealias)
      case _ if key.tpe =:= ru.typeOf[Option[_]].dealias.erasure =>
        optionReader(tpe.tpe.dealias)
      case _ =>
        deriveCaseClassReader(tpe)
    }
  }

  def deriveCaseClassReader(targetType: SafeType0[ru.type]): ConfigReader[_] =
    cv => Try {
      val tpe = targetType.tpe

      if(tpe.typeSymbol.isAbstract || !tpe.typeSymbol.isClass) {
        throw new ConfigReadException(
          s"""Only case classes can be read in config, but type $targetType is a trait or an abstract class.
             | When trying to derive case class reader for $targetType""".stripMargin)
      }

      cv match {
        case obj: ConfigObject =>
          val constructorSymbol = tpe.decl(ru.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
          val params = constructorSymbol.typeSignatureIn(tpe).paramLists.flatten.map {
            p =>
              p.name.toTermName.toString -> p.typeSignatureIn(tpe).finalResultType
          }

          val parsedArgs: List[_] = params.map {
            case (name, typ) =>
              val value = obj.get(name)
              anyReader(SafeType0[ru.type](typ))(value).get
          }

          val reflectedClass = mirror.reflectClass(tpe.typeSymbol.asClass)
          val constructor = reflectedClass.reflectConstructor(constructorSymbol)

          constructor.apply(parsedArgs: _*)
        case _ =>
          throw new ConfigReadException(
            s"""
               |Can't read config value as case class $targetType, config value is not an object.
               | ConfigValue was: $cv""".stripMargin)
      }
    }

  def objectMapReader(mapType: ru.Type): ConfigReader[GenMap[String, _]] = {
    case co: ConfigObject => Try {
      val tyParam = SafeType0[ru.type](mapType.dealias.typeArgs.last)

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

  def listReader(listType: ru.Type): ConfigReader[GenTraversable[_]] = {
    case cl: ConfigList =>
      configListReader(listType, cl)

    case cl: ConfigObject if isList(cl) =>
      val asList = ConfigValueFactory.fromIterable(cl.unwrapped().values())
      configListReader(listType, asList)

    case cv =>
      Failure(new ConfigReadException(
        s"""Can't read config value as a list $listType, config value is not a list.
           | ConfigValue was: $cv""".stripMargin))
  }

  private def configListReader(listType: ru.Type, cl: ConfigList): Try[GenTraversable[_]] = {
    Try {
      val tyParam = SafeType0[ru.type](listType.dealias.typeArgs.last)

      mirror.reflectModule(listType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
        case companionFactory: GenericCompanion[GenTraversable]@unchecked =>
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
  }

  def isList(configObject: ConfigObject): Boolean = {
    configObject.unwrapped().keySet().asScala.forall(_.forall(_.isDigit))
  }

  def optionReader(optionType: ru.Type): ConfigReader[Option[_]] = {
    cv => Try {
      if (cv == null || cv.valueType == ConfigValueType.NULL) {
        None
      } else {
        val tyParam = SafeType0[ru.type](optionType.typeArgs.head)
        Option(anyReader(tyParam)(cv).get)
      }
    }
  }
}

object RuntimeConfigReaderDefaultImpl {
  def apply(codecs: Set[RuntimeConfigReaderCodecs]): RuntimeConfigReaderDefaultImpl =
    new RuntimeConfigReaderDefaultImpl(codecs.map(_.readerCodecs).reduceOption(_ ++ _) getOrElse Map.empty)
}
