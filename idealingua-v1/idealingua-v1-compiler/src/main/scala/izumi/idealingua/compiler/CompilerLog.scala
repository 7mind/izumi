package izumi.idealingua.compiler

trait CompilerLog {
  def log(s: String): Unit
}

object CompilerLog {

  object Default extends CompilerLog {
    override def log(s: String): Unit = println(s)
  }

}
