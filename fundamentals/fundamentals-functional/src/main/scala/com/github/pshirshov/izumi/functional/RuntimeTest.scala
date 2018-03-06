package com.github.pshirshov.izumi.functional

case class RuntimeTest[T](clazz: Class[T]) {
  def unapply(value: AnyRef): Option[T] = {
    value match {
      case g if clazz.isAssignableFrom(g.getClass) => Some(g.asInstanceOf[T])
      case _ => None
    }
  }
}
