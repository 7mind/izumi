package transport
object Platform {
  val name = "native"
  def processId: String = scala.scalanative.posix.unistd.getpid().toString
  def exit(status: Int): Unit = System.exit(status)
  def defer(body: () => Unit): Unit = body()
}
