package izumi.fundamentals.reflection

object ReflectiveCall {
  def call[Out](on: Any, name: String, args: AnyRef*): Out = {
    val mm = on.getClass.getMethods.collectFirst { case m if m.getName == name => m }.get
    val out = mm.invoke(on, args*).asInstanceOf[Out]
    out
  }
}
