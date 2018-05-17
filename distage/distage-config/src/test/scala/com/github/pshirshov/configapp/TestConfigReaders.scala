package com.github.pshirshov.configapp

import com.github.pshirshov.izumi.distage.config.annotations.Conf
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

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


object TestConfigReaders {
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
