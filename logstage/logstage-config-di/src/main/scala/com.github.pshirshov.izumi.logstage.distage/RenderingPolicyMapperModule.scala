package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingPolicy, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.config.codecs.RenderingPolicyCodec.RenderingPolicyMapper

import scala.reflect.runtime.universe

abstract class RenderingPolicyMapperModule[+T <: RenderingPolicy : universe.TypeTag, C : universe.TypeTag] extends BootstrapModuleDef {

  def init(construnctor : C) : T

  many[RenderingPolicyMapper[_ <: RenderingPolicy, _]].add {
    new RenderingPolicyMapper[T, C] {
      override def apply(props: C): T = {
        init(props)
      }
    }
  }
}
