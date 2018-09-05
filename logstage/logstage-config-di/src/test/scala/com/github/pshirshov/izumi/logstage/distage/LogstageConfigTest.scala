package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, ModuleDef}
import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReader, RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.distage.U.StringPolicy
import com.typesafe.config.ConfigFactory
import distage.Injector
import org.scalatest.WordSpec

//
case class Foo(print: List[RenderingPolicy])

case class ConfigWrapper(@ConfPath("rendering") foo: Foo)

class LogstageConfigTest extends WordSpec {
  "Logging module for distage" should {
    "inject loggers" in {

      val config = ConfigFactory.defaultOverrides()
        .withFallback(ConfigFactory.defaultApplication())
        .resolve()

      val configModule = new ConfigModule(AppConfig(config))

      val definition = new ModuleDef {
        make[ConfigWrapper]
      }

      val bootstrapModules = Seq(
        new BootstrapModuleDef {
          many[RenderingPolicyMapper[_ <: RenderingPolicy, _]].add {
            new RenderingPolicyMapper[StringRenderingPolicy, StringPolicy] {
              override def apply(props: StringPolicy): StringRenderingPolicy = {
                new StringRenderingPolicy(props.options, props.renderingLayout)
              }
            }
          }
        }, new LogstageCodecsModule(), configModule)


      val injector = Injector(bootstrapModules: _*)
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[ConfigWrapper].foo.print.size == 2)
    }
  }
}

object U {

  case class StringPolicy(options: RenderingOptions, renderingLayout: Option[String])

}
