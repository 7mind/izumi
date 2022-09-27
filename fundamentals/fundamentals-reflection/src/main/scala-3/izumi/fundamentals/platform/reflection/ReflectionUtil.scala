package izumi.fundamentals.platform.reflection

import scala.quoted.Quotes

object ReflectionUtil {

  /**
    * Returns true if the given type contains no type parameters
    * (this means the type is not "weak" https://stackoverflow.com/questions/29435985/weaktypetag-v-typetag)
    *
    * There is another copy of this snippet in izumi-reflect!
    */
  def allPartsStrong(using q: Quotes)(typeRepr: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    typeRepr.dealias match {
      case x if x.typeSymbol.isTypeParam => false
      case x @ TypeRef(ThisType(_), _) if x.typeSymbol.isAbstractType && !x.typeSymbol.isClassDef => false
      case AppliedType(tpe, args) => allPartsStrong(tpe) && args.forall(allPartsStrong(_))
      case AndType(lhs, rhs) => allPartsStrong(lhs) && allPartsStrong(rhs)
      case OrType(lhs, rhs) => allPartsStrong(lhs) && allPartsStrong(rhs)
      case TypeRef(tpe, _) => allPartsStrong(tpe)
      case TermRef(tpe, _) => allPartsStrong(tpe)
      case ThisType(tpe) => allPartsStrong(tpe)
      case NoPrefix() => true
      case TypeBounds(lo, hi) => allPartsStrong(lo) && allPartsStrong(hi)
      case TypeLambda(_, _, body) => allPartsStrong(body)
      case strange => true
    }
  }

}
