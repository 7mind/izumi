package izumi.distage.reflection.macros.universe.basicuniverse

class ConstructorSelector[U <: scala.reflect.api.Universe & Singleton](val u: U) {
  def selectConstructorMethod(tpe: u.Type): Option[u.MethodSymbol] = {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  @inline private def findConstructor(tpe: u.Type): u.Symbol = {
    findConstructor0(tpe).getOrElse(u.NoSymbol)
  }

  private def findConstructor0(tpe: u.Type): Option[u.Symbol] = {
    tpe match {
      case intersection: u.RefinedTypeApi =>
        intersection.parents.collectFirst(Function.unlift(findConstructor0))
      case tpe =>
        if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isTrait) {
          tpe.baseClasses.collectFirst(Function.unlift(b => if (b ne tpe.typeSymbol) findConstructor0(b.typeSignature) else None))
        } else {
          tpe.decl(u.termNames.CONSTRUCTOR).alternatives.find(_.isPublic)
        }
    }
  }
}
