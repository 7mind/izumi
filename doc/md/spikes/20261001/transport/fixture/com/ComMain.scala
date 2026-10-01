package transport
object ComMain {
  def main(args: Array[String]): Unit = {
    val onMessage: scala.scalajs.js.Function1[String, Unit] = (message: String) => {
      if (message == "QUIT") scala.scalajs.js.Dynamic.global.process.exit(0)
      else {
        require(message.startsWith("SELECT "), "unknown request")
        val suites = message.stripPrefix("SELECT ").split(",")
        Platform.defer(() => {
          val app = new StubApplication(suites, StubApplication.TestsPerSuite)
          app.execute((suite, test) => {
            println("UNSTRUCTURED BODY OUTPUT " + suite + "#" + test)
            scala.scalajs.js.Dynamic.global.scalajsCom.send("EVENT " + suite + "#" + test)
          })
          val report = app.report
          require(report.bodies == suites.length * StubApplication.TestsPerSuite)
          require(report.acquired == 1 && report.released == 1)
          scala.scalajs.js.Dynamic.global.scalajsCom.send("END bodies=" + report.bodies +
            " acquire=" + report.acquired + " release=" + report.released)
        })
      }
    }
    scala.scalajs.js.Dynamic.global.scalajsCom.init(onMessage)
  }
}
