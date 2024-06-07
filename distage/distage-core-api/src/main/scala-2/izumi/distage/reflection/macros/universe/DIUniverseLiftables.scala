package izumi.distage.reflection.macros.universe

import izumi.distage.reflection.macros.universe.basicuniverse.DIUniverseBasicLiftables

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  val basic: DIUniverseBasicLiftables[u.u.type] = DIUniverseBasicLiftables[u.u.type](u.u)
  import basic.{modelReflectionPkg, liftableBasicDIKey}
  import u.Association
  import u.u.*

  implicit val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(info, _, key) =>
      val resultTree = q"{ $modelReflectionPkg.SafeType.get[${Liftable.liftType(info.nonByNameFinalResultType)}] }"
//      val keyTree = liftableBasicDIKey.apply(key).asInstanceOf[Tree]
      // currently only function parameter symbols are spliced by this
      // So, `liftableSafeType` is fine and will work, since parameter
      // types must all be resolved anyway - they cannot contain polymorphic
      // components, unlike general method symbols (info for which we don't generate).
      // (annotations always empty currently)
      val symTree = q"""{ $modelReflectionPkg.SymbolInfo(
      name = ${info.name},
      finalResultType = $resultTree,
      isByName = ${info.isByName},
      wasGeneric = ${info.wasGeneric}
      ) }"""

      q"new $modelReflectionPkg.LinkedParameter($symTree, $key)"
  }

}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
