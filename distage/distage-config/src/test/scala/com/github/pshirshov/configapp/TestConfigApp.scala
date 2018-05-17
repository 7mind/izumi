package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.{AutoConf, Conf, ConfPath}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

// application
trait TestAppService

class TestConfigApp(val services: Set[TestAppService])

// config structures
case class HostPort(port: Int, host: String)

// application services
class DataPuller1(
                   @AutoConf val target: HostPort
                   , @AutoConf val source: HostPort
                 ) extends TestAppService {
  Quirks.discard(source, target)
}

class DataPuller2(
                   @AutoConf val target: HostPort
                   , @AutoConf val source: HostPort
                 ) extends TestAppService {
  Quirks.discard(source, target)
}


class DataPuller3(
                   @Conf("cassandra") val target: HostPort
                   , @ConfPath("datasource.google") val source: HostPort
                 ) extends TestAppService {
  Quirks.discard(source, target)
}

// Trivial injections
class HttpServer1(@AutoConf val listenOn: HostPort) extends TestAppService {
  Quirks.discard(listenOn)
}

class HttpServer2(@AutoConf val listenOn: HostPort) extends TestAppService {
  Quirks.discard(listenOn)
}

class HttpServer3(@AutoConf val listenOn: HostPort) extends TestAppService {
  Quirks.discard(listenOn)
}

object TestConfigApp {
  final val definition = new ModuleDef {
    make[HttpServer1]
    make[HttpServer2]
    make[HttpServer3]

    make[DataPuller1]
    make[DataPuller2]
    make[DataPuller3]

    make[TestAppService].named("puller1").using[DataPuller1]
    make[TestAppService].named("puller2").using[DataPuller2]
    make[TestAppService].named("puller3").using[DataPuller3]

    make[TestAppService].named("puller4").from[DataPuller1]
    make[TestAppService].named("puller5").from[DataPuller2]
    make[TestAppService].named("puller6").from[DataPuller3]

    many[TestAppService]
      .ref[HttpServer1]
      .ref[HttpServer2]
      .ref[HttpServer3]

    many[TestAppService].named("puller1")
      .ref[TestAppService]("puller1")

    many[TestAppService].named("puller2")
      .ref[TestAppService]("puller2")

    many[TestAppService].named("puller3")
      .ref[TestAppService]("puller3")

    many[TestAppService].named("puller4")
      .ref[TestAppService]("puller4")

    many[TestAppService].named("puller5")
      .ref[TestAppService]("puller5")

    many[TestAppService].named("puller6")
      .ref[TestAppService]("puller6")

    make[TestConfigApp]
  }
}
