package izumi.idealingua.compiler

object ShutdownImpl extends Shutdown {
  private val log = CompilerLog.Default
  def shutdown(message: String): Nothing = {
    log.log(message)
    System.out.flush()
    System.err.flush()
    System.exit(1)
    throw new IllegalArgumentException(message)
  }
}
