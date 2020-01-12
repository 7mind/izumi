package izumi.thirdparty.internal.boopickle

private[boopickle] object ReferenceEquality {
  @inline def eq(a: AnyRef, b: AnyRef): Boolean  = a eq b
  @inline def ne(a: AnyRef, b: AnyRef): Boolean  = a ne b
  @inline def identityHashCode(obj: AnyRef): Int = System.identityHashCode(obj)
}
