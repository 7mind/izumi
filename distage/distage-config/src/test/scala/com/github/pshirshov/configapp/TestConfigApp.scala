package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.{AutoConf, Conf, ConfPath}
import com.github.pshirshov.izumi.distage.model.definition.{Id, ModuleDef}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

case class HostPort(port: Int, host: String)

trait Endpoint {
  def address: HostPort
}

class EndpoitImpl(@AutoConf val address: HostPort) extends Endpoint {
  Quirks.discard(address)
}

class CassandraEndpoint(@Conf("cassandra") val cassAddress: HostPort) extends Endpoint {
  override def address: HostPort = cassAddress
}

trait TestService

case class TestService1(
                    @Id("service1") listener: Endpoint
                    , @Conf("cassandra") cassAddress: HostPort
                    , @ConfPath("absolute.path") googleAddress: HostPort
                  ) extends TestService {
  Quirks.discard(listener, cassAddress, googleAddress)
}

class TestService2(@Id("service2") listener: Endpoint, cendpoint: CassandraEndpoint) extends TestService {
  Quirks.discard(listener, cendpoint)
}

class TestService3(listener: Endpoint) extends TestService {
  Quirks.discard(listener)
}

class TestConfigApp(val services: Set[TestService])

case class MapCaseClass(@Conf("mymap") m: Map[String, HostPort])

case class ListCaseClass(@Conf("mylist") l: List[Set[Wrapper[HostPort]]])

case class OptionCaseClass(
                            @Conf("myoptint") optInt: Option[Int]
                            , @Conf("myoptdouble") optDouble: Option[Double]
                            , @Conf("myoptstring") optString: Option[String]
                          )

case class OptionCaseClass2(@Conf("opt") opt: Opt)

case class Opt(optInt: Option[Int])

case class Wrapper[A](wrap: A)

object TestConfigApp {
  final val definition = new ModuleDef {
    make[TestConfigApp]
    make[TestService1]

    many[TestService]
      .add[TestService1]
      .add[TestService2]
      .add[TestService3]
    make[Endpoint].named("service1").from[EndpoitImpl]
    make[Endpoint].named("service2").from[EndpoitImpl]
    make[Endpoint].from[EndpoitImpl]
    make[CassandraEndpoint]
  }

  final val mapDefinition = new ModuleDef {
    make[MapCaseClass]
  }

  final val listDefinition = new ModuleDef {
    make[ListCaseClass]
  }

  final val optDefinition = new ModuleDef {
    make[OptionCaseClass]
    make[OptionCaseClass2]
  }
}
