package izumi.distage.reflection.macros.universe.impl

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType { this: DIUniverseBase =>

  class MacroSafeType private (
//    private[reflection] val typeNative: TypeNative,
    private[reflection] val tagTree: ctx.Tree,
    private[MacroSafeType] val tag: LightTypeTag,
  ) {

    // hashcode is already cached in the underlying code
    @inline override def hashCode: Int = {
      tag.hashCode()
    }

    @inline override def toString: String = {
      tag.repr
    }
    override final def equals(obj: Any): Boolean = {
      obj match {
        case that: MacroSafeType =>
          tag =:= that.tag
        case _ =>
          false
      }
    }
  }

  object MacroSafeType {
    import ctx.universe.*
    protected[this] val modelReflectionPkg: ctx.Tree = q"_root_.izumi.distage.model.reflection"

    def create(tpe: TypeNative): MacroSafeType = {
      val ltt = LightTypeTagImpl.makeLightTypeTag(u)(tpe)
      val tagTree = q"{ $modelReflectionPkg.SafeType.get[${Liftable.liftType(tpe.asInstanceOf[ctx.Type])}] }"

      new MacroSafeType(tagTree, ltt)
    }
  }

}
