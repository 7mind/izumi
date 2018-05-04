package com.github.pshirshov.izumi.distage.provisioning.strategies.`macro`

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse

object MacroTools {

  def annotationsForDIKey(u: DIUniverse)(key: u.DIKey): u.u.Modifiers = {
    import u.u._

    key match {
      case idKey: u.DIKey.IdKey[_] =>
        import idKey._
        val ann = q"""new ${typeOf[Id]}($id)"""
        Modifiers.apply(NoFlags, typeNames.EMPTY, List(ann))
      case _ =>
        Modifiers()
    }
  }

}
