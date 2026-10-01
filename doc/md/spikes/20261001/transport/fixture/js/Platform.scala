package transport
object Platform {
  val name = "js"
  def processId: String = scala.scalajs.js.Dynamic.global.process.pid.toString
  def exit(status: Int): Unit = { scala.scalajs.js.Dynamic.global.process.exit(status); () }
  def defer(body: () => Unit): Unit = {
    println("JS_SCHEDULE")
    scala.scalajs.js.timers.setTimeout(20) {
      println("JS_CALLBACK")
      body()
    }
  }
}
