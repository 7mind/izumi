package com.github.pshirshov.izumi.logstage.config.api.defaults

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy.PolicyConfig
import com.github.pshirshov.izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.config.codecs.LogSinkCodec.LogSinkMapper
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import com.typesafe.config.Config

object DefaultMapperrs {

  object renderingPolicies {
    val jsonRenderingPolicyMapper: RenderingPolicyMapper[LogstageCirceRenderingPolicy] = new RenderingPolicyMapper[LogstageCirceRenderingPolicy] {
      override def instantiate(policyConfig: RenderingPolicy.PolicyConfig): LogstageCirceRenderingPolicy = {
        new LogstageCirceRenderingPolicy(policyConfig.prettyPrint)
      }
    }

    val stringRenderingPolicyMapper: RenderingPolicyMapper[StringRenderingPolicy] = new RenderingPolicyMapper[StringRenderingPolicy] {
      override def instantiate(policyConfig: PolicyConfig): StringRenderingPolicy = {
        new StringRenderingPolicy(options = RenderingOptions(policyConfig.withColors, policyConfig.withExceptions), renderingLayout = policyConfig.renderingLayout)
      }
    }
  }

  object sinks {
    val consoleSinkMapper: LogSinkMapper[ConsoleSink] = new LogSinkMapper[ConsoleSink] {
      override def instantiate(config: Config, renderingPolicy: RenderingPolicy): ConsoleSink = {
        Quirks.discard(config)
        new ConsoleSink(renderingPolicy)
      }
    }
  }
}
