package izumi.idealingua.compiler

trait Shutdown {
  def shutdown(message: String): Nothing
}
