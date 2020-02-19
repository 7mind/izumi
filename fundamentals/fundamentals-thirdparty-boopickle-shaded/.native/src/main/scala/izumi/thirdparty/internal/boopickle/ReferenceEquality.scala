package izumi.thirdparty.internal.boopickle

object ReferenceEquality {
  @inline def eq(a: AnyRef, b: AnyRef): Boolean  = a == b
  @inline def ne(a: AnyRef, b: AnyRef): Boolean  = a != b
  @inline def identityHashCode(obj: AnyRef): Int = obj.hashCode()
}
