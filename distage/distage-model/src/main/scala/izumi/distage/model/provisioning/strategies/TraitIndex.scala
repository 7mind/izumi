package izumi.distage.model.provisioning.strategies

import java.lang.reflect.Method

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

final case class TraitIndex(
  methods: Map[Method, Association.AbstractMethod],
  getters: Map[String, TraitField],
  setters: Map[String, TraitField]
)
