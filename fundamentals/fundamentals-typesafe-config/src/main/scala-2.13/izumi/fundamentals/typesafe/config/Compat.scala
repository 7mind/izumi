//package izumi.fundamentals.typesafe.config
//
//import izumi.fundamentals.platform.strings.IzString._
//import izumi.fundamentals.reflection.SafeType0
//import com.typesafe.config.{ConfigList, ConfigObject}
//
//import scala.jdk.CollectionConverters._
//import scala.collection.immutable.Queue
//import scala.collection.{GenMap, Iterable, IterableFactory, MapFactory}
//import scala.reflect.runtime.{universe => ru}
//import scala.util.{Failure, Try}
//
//private[config] object Compat {
//
//  def objectMapReader(mirror: ru.Mirror, anyReader: SafeType0[ru.type] => ConfigReader[_])(mapType: ru.Type): ConfigReader[GenMap[String, _]] = {
//    case co: ConfigObject => Try {
//      val tyParam = SafeType0(mapType.dealias.typeArgs.last)
//
//      mirror.reflectModule(mapType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
//        case companionFactory: MapFactory[GenMap] @unchecked =>
//
//          val map = co.asScala
//
//          val parsedResult = map.foldLeft[Either[Queue[String], Queue[(String, Any)]]](Right(Queue.empty)) {
//            (e, kv) => kv match {
//              case (key, value) =>
//                val res = anyReader(tyParam)(value).fold(
//                  exc => Left(s"Couldn't parse key `$key` because of exception: ${exc.getMessage}")
//                  , Right(_)
//                )
//
//                res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ (key -> v)))
//            }
//          }
//
//          val parsedArgs = parsedResult.fold(
//            errors => throw new ConfigReadException(
//              s"""Couldn't read config object as Map type $mapType, there were errors when trying to parse it's fields from value: $co
//                 |The errors were:
//                 |  ${errors.niceList()}
//               """.stripMargin
//            )
//            , identity
//          )
//
//          val res = companionFactory.newBuilder.++=(parsedArgs).result()
//          res
//        case c =>
//          throw new ConfigReadException(
//            s"""When trying to read a Map type $mapType: can't instantiate class. Expected a companion object of type
//               | scala.collection.generic.GenMapFactory to be present, but $mapType companion object has type: ${c.getClass}.
//               |
//               | ConfigValue was: $co""".stripMargin)
//      }
//    }
//    case cv =>
//      Failure(new ConfigReadException(
//        s"""Can't read config value as a map $mapType, config value is not an object.
//           | ConfigValue was: $cv""".stripMargin))
//  }
//
//  def configListReader(mirror: ru.Mirror, anyReader: SafeType0[ru.type] => ConfigReader[_])(listType: ru.Type, cl: ConfigList): Try[Iterable[_]] = {
//    Try {
//      val tyParam = SafeType0(listType.dealias.typeArgs.last)
//
//      mirror.reflectModule(listType.dealias.companion.typeSymbol.asClass.module.asModule).instance match {
//        case companionFactory: IterableFactory[Iterable] @unchecked =>
//          val list = cl.asScala
//
//          val parsedResult = list.zipWithIndex.foldLeft[Either[Queue[String], Queue[Any]]](Right(Queue.empty)) {
//            (e, configValue) => configValue match {
//              case (value, idx) =>
//                val res = anyReader(tyParam)(value).fold(
//                  exc => Left(s"Couldn't parse value at index `${idx+1}` of a list $listType because of exception: ${exc.getMessage}")
//                  , Right(_)
//                )
//
//                res.fold(msg => Left(e.left.getOrElse(Queue.empty) :+ msg), v => e.map(_ :+ v))
//            }
//          }
//
//          val parsedArgs = parsedResult.fold(
//            errors => throw new ConfigReadException(
//              s"""Couldn't read config list as a collectionType $listType, there were errors when trying to parse it's fields from value: $cl
//                 |The errors were:
//                 |  ${errors.niceList()}
//               """.stripMargin
//            )
//            , identity
//          )
//
//          companionFactory(parsedArgs: _*)
//        case c =>
//          throw new ConfigReadException(
//            s"""When trying to read a collection type $listType: can't instantiate class. Expected a companion object of type
//               | scala.collection.generic.GenericCompanion to be present, but $listType companion object has type: ${c.getClass}.
//               |
//               | ConfigValue was: $cl""".stripMargin)
//      }
//    }
//  }
//
//}
