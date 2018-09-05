//package com.github.pshirshov.izumi.logstage.config.codecs
//
//import com.github.pshirshov.izumi.fundamentals.typesafe.config.ConfigReader
//import com.github.pshirshov.izumi.logstage.api.Log
//import com.github.pshirshov.izumi.logstage.api.config.LoggerPathConfig
//import com.typesafe.config.{ConfigObject, ConfigValue, ConfigValueType}
//
//import scala.collection.JavaConverters._
//import scala.util.{Failure, Try}
//
//class LoggerConfigCodec(sinkCodec: LogSinkCodec) extends ConfigReader[LoggerPathConfig] {
//  override def apply(configValue: ConfigValue): Try[LoggerPathConfig] = {
//    val result = configValue.valueType() match {
//      case ConfigValueType.OBJECT =>
//        val cfg = configValue.asInstanceOf[ConfigObject].toConfig
//        for {
//          level <- Try(cfg.getString("threshold")).map(Log.Level.parse)
//          sinkIds <- Try(cfg.getList("sinks")).map(_.unwrapped()).map(_.asScala.toList.map(_.asInstanceOf[String]))
//        } yield (level, sinkIds)
//      case ConfigValueType.STRING =>
//        for {
//          res <- Try(configValue.unwrapped().asInstanceOf[String]).map(Log.Level.parse)
//        } yield (res, List(LogSinkCodec.configKeyDefaultIdentity))
//      case _ =>
//        Failure(new IllegalArgumentException("Illegal config format exception. It must be either Object or String"))
//    }
//    result.flatMap {
//      case (level, ids) =>
//        Try {
//          val (unknowns, sinks) = ids.map(id => id -> sinkCodec.fetchLogSink(id)).partition(_._2.isEmpty)
//          if (unknowns.nonEmpty) {
//            throw new IllegalArgumentException(s"Undefined LogSink ids : ${unknowns.map(_._1).mkString(", ")}")
//          } else
//            LoggerPathConfig(level, sinks collect {
//              case (_, Some(sink)) => sink
//            })
//        }
//    }
//  }
//}
