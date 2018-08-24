package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

class TagMacro(val c: blackbox.Context) {
  import c.universe._

  private val logger = MacroUtil.mkLogger[ProviderMagnetMacro](c)

  def impl[T: c.WeakTypeTag]: c.Expr[RuntimeDIUniverse.Tag[T]] = {
    val targetTypeTag = weakTypeOf[RuntimeDIUniverse.u.TypeTag[T]]
//    val targetTypeTag = weakTypeOf[scala.reflect.runtime.universe.TypeTag[T]]

    val foundTypeTag = c.Expr[scala.reflect.runtime.universe.TypeTag[T]] {
      c.inferImplicitValue(targetTypeTag, silent = false)
    }

    val res = reify[RuntimeDIUniverse.Tag[T]] {
      RuntimeDIUniverse.Tag(foundTypeTag.splice)
    }

    logger.log(s"Final code of Tag[$weakTypeOf[T]]:\n ${showCode(res.tree)}")

    if (weakTypeOf[T] =:= TypeTag.Nothing.tpe) {
      logger.log("Oh shit, it's a Nothing!")

      reify(RuntimeDIUniverse.Tag.tagNothing)
        .asInstanceOf[c.Expr[RuntimeDIUniverse.Tag[T]]]
    } else {
      res
    }
  }
}

object TagMacro {
  def get[T]: RuntimeDIUniverse.Tag[T] = macro TagMacro.impl[T]

  // implicit def getOfTT[T: TypeTag] = Tag(typeTag[TT])
}
