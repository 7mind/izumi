package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.distage.U.{ConsoleSinkConstructor, StringPolicyConstructor}
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import com.typesafe.config.ConfigFactory
import distage.Injector
import org.scalatest.WordSpec

case class ConfigWrapper(@ConfPath("logstage") loggerConfig: LoggerConfig)

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
        new RenderingPolicyMapperModule[StringRenderingPolicy, StringPolicyConstructor] {
          override def init(construnctor: StringPolicyConstructor): StringRenderingPolicy = {
            new StringRenderingPolicy(construnctor.options, construnctor.renderingLayout)
          }
        },
        new LogSinkMapperModule[ConsoleSink, ConsoleSinkConstructor] {
          override def initLogSink(props: ConsoleSinkConstructor): ConsoleSink = {
            new ConsoleSink(props.policy)
          }
        },


        new LogstageCodecsModule(), configModule)


      val injector = Injector(bootstrapModules: _*)
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val wrapper = context.get[ConfigWrapper].loggerConfig
      println(wrapper)
    }
  }
}

object U {

  case class ConsoleSinkConstructor(policy : RenderingPolicy)
  case class StringPolicyConstructor(options: RenderingOptions, renderingLayout: Option[String])

}
