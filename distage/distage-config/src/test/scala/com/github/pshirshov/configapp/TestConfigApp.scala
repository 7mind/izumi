package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.{AutoConf, Conf}
import com.github.pshirshov.izumi.distage.model.definition.{Id, TrivialModuleDef}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

case class HostPort(port: Int, host: String)

trait Listener {}

class ListenerImpl(@AutoConf listen: HostPort) extends Listener {
  Quirks.discard(listen)
}

class CassandraDriver(@Conf("cassandra") listen: HostPort) extends Listener {
  Quirks.discard(listen)
}


trait TestService

class TestService1(@Id("service1") listener: Listener, @Conf("cassandra") cassandra: HostPort) extends TestService {
  Quirks.discard(listener)
}

class TestService2(@Id("service2") listener: Listener) extends TestService {
  Quirks.discard(listener)
}

class TestService3(listener: Listener) extends TestService {
  Quirks.discard(listener)
}

class TestConfigApp(val services: Set[TestService])


object TestConfigApp {
  final val definition = TrivialModuleDef
    .bind[TestConfigApp]
    .set[TestService]
    .element[TestService1]
    .element[TestService2]
    .element[TestService3]
    .bind[Listener].named("service1").as[ListenerImpl]
    .bind[Listener].named("service2").as[ListenerImpl]
    .bind[Listener].as[ListenerImpl]
    .bind[CassandraDriver]
}
