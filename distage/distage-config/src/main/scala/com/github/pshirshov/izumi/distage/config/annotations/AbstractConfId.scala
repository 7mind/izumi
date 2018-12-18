package com.github.pshirshov.izumi.distage.config.annotations

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait AbstractConfId

sealed trait AutomaticConfId extends AbstractConfId {
  def contextKey: DIKey
}

final case class AutoConfId(contextKey: DIKey, parameterName: String) extends AutomaticConfId {
  override def toString: String = s"${contextKey.toString}#cfg-auto-$parameterName"
}

final case class ConfId(contextKey: DIKey, parameterName: String, nameOverride: String) extends AutomaticConfId {
  override def toString: String = s"${contextKey.toString}#cfg-id-$nameOverride->$parameterName"
}

final case class ConfPathId(contextKey: DIKey, parameterName: String, pathOverride: String) extends AbstractConfId {
  override def toString: String = s"${contextKey.toString}#cfg-path$pathOverride->$parameterName"

  override def equals(other: Any): Boolean = other match {
    case that: ConfPathId =>
      contextKey == that.contextKey &&
        pathOverride == that.pathOverride
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(contextKey, pathOverride)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}


