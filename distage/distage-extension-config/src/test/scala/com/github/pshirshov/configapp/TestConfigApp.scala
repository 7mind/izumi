package com.github.pshirshov.configapp

import distage.Id
import distage.config.{AppConfig, ConfigModuleDef}
import izumi.distage.config.extractor.ConfigPathExtractor.ResolvedConfig
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.language.Quirks

// application
trait TestAppService

class TestConfigApp(val services: Set[TestAppService], val usedConfig: ResolvedConfig, val appConfig: AppConfig)

// config structures
case class HostPort(port: Int, host: String)

// application services
class DataPuller1(
                   val target: HostPort @Id("DataPuller1.target"),
                   val source: HostPort @Id("DataPuller1.source"),
                 ) extends TestAppService {
  Quirks.discard(source, target)
}

class DataPuller2(
                   val target: HostPort @Id("DataPuller2.target"),
                   val source: HostPort @Id("DataPuller2.source"),
                 ) extends TestAppService {
  Quirks.discard(source, target)
}

class DataPuller3(
                   val target: HostPort @Id("cassandra"),
                   val source: HostPort @Id("datasource.google"),
                   val s: String @Id("scalars.s"),
                 ) extends TestAppService {
  Quirks.discard(source, target)
}

// Trivial injections
class HttpServer1(val listenOn: HostPort @Id("HttpServer1.HostPort")) extends TestAppService {
  Quirks.discard(listenOn)
}

class HttpServer2(val listenOn: HostPort @Id("HttpServer2.HostPort.listenOn")) extends TestAppService {
  Quirks.discard(listenOn)
}

object TestConfigApp {
  final val definition = PlannerInput.noGc(new ConfigModuleDef {
    make[HttpServer1]
    make[HttpServer2]
    makeConfigNamed[HostPort]("HttpServer1.HostPort")
    makeConfigNamed[HostPort]("HttpServer2.HostPort.listenOn")

    make[DataPuller1]
    makeConfigNamed[HostPort]("DataPuller1.target")
    makeConfigNamed[HostPort]("DataPuller1.source")

    make[DataPuller2]
    makeConfigNamed[HostPort]("DataPuller2.target")
    makeConfigNamed[HostPort]("DataPuller2.source")

    make[DataPuller3]
    makeConfigNamed[HostPort]("cassandra")
    makeConfigNamed[HostPort]("datasource.google")
    makeConfigNamed[String]("scalars.s")

    make[TestAppService].named("puller1").using[DataPuller1]
    make[TestAppService].named("puller2").using[DataPuller2]
    make[TestAppService].named("puller3").using[DataPuller3]

    many[TestAppService]
      .ref[HttpServer1]
      .ref[HttpServer2]
      .ref[TestAppService]("puller1")
      .ref[TestAppService]("puller2")
      .ref[TestAppService]("puller3")

    make[TestConfigApp]
  })

  final val setDefinition = PlannerInput.noGc(new ConfigModuleDef {
    many[TestAppService].add[DataPuller1]
    makeConfigNamed[HostPort]("DataPuller1.target")
    makeConfigNamed[HostPort]("DataPuller1.source")
  })
}
