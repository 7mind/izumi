package izumi.distage.reflection.macros.universe.basicuniverse

import scala.reflect.macros.blackbox

class DIUniverseBasicLiftables[U <: scala.reflect.api.Universe](val ctx: blackbox.Context) {
  val u: U = ctx.universe.asInstanceOf[U]
  import u.*

  val modelReflectionPkg: Tree = q"_root_.izumi.distage.model.reflection"

  implicit val liftableMacroSafeType: Liftable[MacroSafeType] = {
    stpe =>
      stpe.tagTree.asInstanceOf[Tree]
  }

  implicit val liftableCompactParameter: Liftable[CompactParameter] = {
    case CompactParameter(info, _, key) =>
      val symTree = q"""{ $modelReflectionPkg.SymbolInfo(
      name = ${info.name},
      finalResultType = ${info.safeFinalResultType},
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""
      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }

  implicit val liftableBasicDIKey: Liftable[MacroDIKey.BasicKey] = {
    Liftable[MacroDIKey.BasicKey] {
      case t: MacroDIKey.TypeKey => q"{ new $modelReflectionPkg.DIKey.TypeKey(${t.tpe}) }"
      case i: MacroDIKey.IdKey => q"{ new $modelReflectionPkg.DIKey.IdKey(${i.tpe}, ${i.id}) }"
    }
  }

}

object DIUniverseBasicLiftables {
  def apply[U <: scala.reflect.api.Universe](c: blackbox.Context): DIUniverseBasicLiftables[U] = new DIUniverseBasicLiftables[U](c)
}
