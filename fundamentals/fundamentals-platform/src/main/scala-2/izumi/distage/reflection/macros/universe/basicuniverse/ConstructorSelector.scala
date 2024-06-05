package izumi.distage.reflection.macros.universe.basicuniverse

class ConstructorSelector(val u: scala.reflect.api.Universe) {
  def selectConstructorMethod(tpe: u.Type): Option[scala.reflect.api.Universe#MethodSymbol] = {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  @inline private[this] def findConstructor(tpe: u.Type): scala.reflect.api.Universe#Symbol = {
    findConstructor0(tpe).getOrElse(u.NoSymbol)
  }

  private[this] def findConstructor0(tpe: u.Type): Option[scala.reflect.api.Universe#Symbol] = {
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
