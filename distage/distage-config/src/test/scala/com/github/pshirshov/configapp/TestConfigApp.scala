package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.pureconfig.WithPureConfig
import com.github.pshirshov.izumi.distage.config.pureconfig.WithPureConfig.R
import com.github.pshirshov.izumi.distage.config.{AutoConf, Conf}
import com.github.pshirshov.izumi.distage.model.definition.{Id, TrivialModuleDef}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

case class HostPort(port: Int, host: String)

object HostPort extends WithPureConfig[HostPort] {
  override def reader: R[HostPort] = implicitly
}

trait Endpoint {
  def address: HostPort
}

class EndpoitImpl(@AutoConf val address: HostPort) extends Endpoint {
  Quirks.discard(address)
}

class CassandraEndpoint(@Conf("cassandra") val address: HostPort) extends Endpoint {
  Quirks.discard(address)
}


trait TestService

class TestService1(@Id("service1") listener: Endpoint, @Conf("cassandra") cassandra: HostPort) extends TestService {
  Quirks.discard(listener)
}

class TestService2(@Id("service2") listener: Endpoint) extends TestService {
  Quirks.discard(listener)
}

class TestService3(listener: Endpoint) extends TestService {
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
    .bind[Endpoint].named("service1").as[EndpoitImpl]
    .bind[Endpoint].named("service2").as[EndpoitImpl]
    .bind[Endpoint].as[EndpoitImpl]
    .bind[CassandraEndpoint]
}
