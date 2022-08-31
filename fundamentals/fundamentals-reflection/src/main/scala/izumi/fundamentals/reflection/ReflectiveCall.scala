package izumi.fundamentals.reflection

object ReflectiveCall {
  def call[Out](on: Any, name: String, args: Any*): Out = {
    val mm = on.getClass.getMethods.collect { case m if m.getName == name => m }.head
    val out = mm.invoke(on, args: _*).asInstanceOf[Out]
    out
  }
}
