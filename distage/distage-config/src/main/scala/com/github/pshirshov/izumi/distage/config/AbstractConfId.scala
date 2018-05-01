package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait AbstractConfId

final case class AutoConfId(context: DIKey) extends AbstractConfId {
  override def toString: String = s"auto[${context.toString}]"
}

object AutoConfId {

  import u._

  implicit final val liftableTypeKey: Liftable[AutoConfId] = {
    case AutoConfId(symbol) => q"""
        { new _root_.com.github.pshirshov.izumi.distage.config.AutoConfId($symbol) }
          """
  }

  implicit final val contract: IdContract[AutoConfId] = new IdContract[AutoConfId]
}


final case class ConfId(context: String) extends AbstractConfId {
  override def toString: String = s"ctx[${context.toString}]"
}

object ConfId {

  import u._

  implicit final val liftableTypeKey: Liftable[ConfId] = {
    case ConfId(symbol) => q"""
        { new _root_.com.github.pshirshov.izumi.distage.config.ConfId($symbol) }
          """
  }

  implicit final val contract: IdContract[ConfId] = new IdContract[ConfId]
}
