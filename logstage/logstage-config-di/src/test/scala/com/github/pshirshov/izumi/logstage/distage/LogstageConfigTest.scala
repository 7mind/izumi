package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, ModuleDef}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.distage.LogstageConfigTest.TestRenderingPolicy.TestRenderingPolicyConstructor
import com.github.pshirshov.izumi.logstage.distage.LogstageConfigTest._
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import com.typesafe.config.ConfigFactory
import distage.Injector
import org.scalatest.{Assertion, WordSpec}

import scala.util.Try

class LogstageConfigTest extends WordSpec {

  "Logging module for distage" should {

    "parse log level correctly" in {
      val config = ConfigFactory.parseString(
        """
          | good {
          |   level = info
          | }
          | bad {
          |   level = unknown
          | }
        """.stripMargin)

      val configModule = new ConfigModule(AppConfig(config))

      val bootstrapModules = Seq(
        new LogstageCodecsModule(), configModule
      )

      val goodDef = new ModuleDef {
        make[ThresholdWrapper].from {
          info: ThresholdWrapper@ConfPath("good") =>
            info
        }
      }

      withCtx(bootstrapModules, goodDef).asGood {
        l =>
          assert(l.get[ThresholdWrapper].level == Log.Level.Info)
      }

      val badDef = new ModuleDef {
        make[ThresholdWrapper].named("bad").from {
          info: ThresholdWrapper@ConfPath("bad") =>
            info
        }
      }

      withCtx(bootstrapModules, badDef).asBad()
    }

    "parse rendering policy instance" in {
      val config = ConfigFactory.parseString(
        """
          | good {
          |   policy {
          |     path = "com.github.pshirshov.izumi.logstage.distage.LogstageConfigTest.TestRenderingPolicy"
          |     params {
          |       foo = 1
          |       bar = "1"
          |     }
          |   }
          | }
          | badConstructor {
          |   policy {
          |     path = "com.github.pshirshov.izumi.logstage.distage.LogstageConfigTest.TestRenderingPolicy"
          |     params {
          |       xyz = 1
          |     }
          |   }
          | }
          | badLogSink {
          |   policy {
          |   path = "com.github.pshirshov.izumi.logstage.distage.LogstageConfigTest.TestRenderingPolicy2"
          |   params {
          |     xyz = 1
          |   }
          |   }
          | }
          |
        """.stripMargin)

      val configModule = new ConfigModule(AppConfig(config))

      val bootstrapModules = Seq(
        new RenderingPolicyMapperModule[TestRenderingPolicy, TestRenderingPolicyConstructor] {
          override def init(construnctor: TestRenderingPolicyConstructor): TestRenderingPolicy = {
            new TestRenderingPolicy(construnctor.foo, construnctor.bar)
          }
        },
        new LogstageCodecsModule()
        , configModule
      )

      val goodDef = new ModuleDef {
        make[RenderingPolicyWrapper].from {
          instance: RenderingPolicyWrapper@ConfPath("good") =>
            instance
        }
      }

      withCtx(bootstrapModules, goodDef).asGood {
        l =>
          val p = l.get[RenderingPolicyWrapper].policy
          assert(p.isInstanceOf[TestRenderingPolicy])
      }

      val bad1Def = new ModuleDef {
        make[RenderingPolicyWrapper].from {
          instance: RenderingPolicyWrapper@ConfPath("badConstructor") =>
            instance
        }
      }

      withCtx(bootstrapModules, bad1Def).asBad()

      val bad2Def = new ModuleDef {
        make[RenderingPolicyWrapper].from {
          instance: RenderingPolicyWrapper@ConfPath("badLogSink") =>
            instance
        }
      }

      withCtx(bootstrapModules, bad2Def).asBad()
    }

    "fail on loglevel parse without default sink" in {

      val config = ConfigFactory.parseString(
        """
          |logstage {
          |  sinks = {
          |    "sink1" = {
          |      path = "com.github.pshirshov.izumi.logstage.sink.ConsoleSink"
          |      params {
          |        policy {
          |          path = "com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy"
          |          params {
          |            options {
          |              withExceptions = false
          |              withColors = true
          |            }
          |          }
          |        }
          |      }
          |    }
          |  }
          |
          |
          |  root {
          |    threshold = "info"
          |    sinks = [
          |      "sink1"
          |    ]
          |  }
          |
          |  entries = {
          |    "com.github.pshirshov" = "warn"
          |  }
          |}
          |
        """.stripMargin)

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
          override def init(props: ConsoleSinkConstructor): ConsoleSink = {
            new ConsoleSink(props.policy)
          }
        }
        , new LogstageCodecsModule()
        , configModule
      )

      withCtx(bootstrapModules, definition, new LoggerConfigModule).asBad()

    }


    "inject loggers" in {

      val config = ConfigFactory.parseString(
        """
          |logstage {
          |  sinks = {
          |    "default" = {
          |      path = "com.github.pshirshov.izumi.logstage.sink.ConsoleSink"
          |      params {
          |        policy {
          |          path = "com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy"
          |          params {
          |            options {
          |              withExceptions = false
          |              withColors = true
          |            }
          |          }
          |        }
          |      }
          |    }
          |    "sink1" = {
          |      path = "com.github.pshirshov.izumi.logstage.sink.ConsoleSink"
          |      params {
          |        policy {
          |          path = "com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy"
          |          params {
          |            options {
          |              withExceptions = false
          |              withColors = true
          |            }
          |          }
          |        }
          |      }
          |    }
          |    "sink2" = {
          |      path = "com.github.pshirshov.izumi.logstage.sink.ConsoleSink"
          |      params {
          |        policy {
          |          path = "com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy"
          |          params {
          |            options {
          |              withExceptions = false
          |              withColors = true
          |            }
          |          }
          |        }
          |      }
          |    }
          |  }
          |
          |
          |  root {
          |    threshold = "info"
          |    sinks = [
          |      "default"
          |    ]
          |  }
          |
          |  entries = {
          |    "com.github.pshirshov" = "warn"
          |    "ss" = {
          |      threshold = "debug"
          |      sinks = [
          |        "sink1", "sink2"
          |      ]
          |    }
          |  }
          |}
          |
        """.stripMargin)

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
          override def init(props: ConsoleSinkConstructor): ConsoleSink = {
            new ConsoleSink(props.policy)
          }
        }
        , new LogstageCodecsModule()
        , configModule
      )

      withCtx(bootstrapModules, definition, new LoggerConfigModule).asGood {
        l =>
          val wrapper = l.get[ConfigWrapper].sinksList
      }

    }
  }


  class LocatorWrapper(locator: Try[Locator]) {
    def asBad(): Assertion = {
      assert(locator.isFailure)
    }

    def asGood[T](f: Locator => T): T = {
      assert(locator.isSuccess)
      f(locator.get)
    }
  }

  private def withCtx(bootstapModules: Seq[BootstrapModuleDef], modules: ModuleDef*): LocatorWrapper = {
    val injector = Injector(bootstapModules: _*)

    val goodCtx = Try {
      val plan = injector.plan(modules.toList.overrideLeft)
      injector.produce(plan)
    }
    new LocatorWrapper(goodCtx)
  }
}

object LogstageConfigTest {


  case class ThresholdWrapper(level: Log.Level)

  class TestRenderingPolicy(foo: Int, bar: Option[String]) extends RenderingPolicy {
    override def render(entry: Log.Entry): String = entry.toString
  }

  object TestRenderingPolicy {

    case class TestRenderingPolicyConstructor(foo: Int, bar: Option[String])

  }

  class TestSink(policy: RenderingPolicy) extends LogSink {
    override def flush(e: Log.Entry): Unit = {
      Quirks.discard(e)
    }
  }

  object TestSink {

    case class Constructor(policy: RenderingPolicy)

  }

  case class RenderingPolicyWrapper(policy: RenderingPolicy)

  case class ConsoleSinkConstructor(policy: RenderingPolicy)

  case class StringPolicyConstructor(options: RenderingOptions, renderingLayout: Option[String])

  case class SinksList(sinks: Map[String, LogSink])

  case class ConfigWrapper(@ConfPath("logstage") sinksList: SinksList)

}
