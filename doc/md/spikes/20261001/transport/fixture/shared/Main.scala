package transport
object Main {
  def main(args: Array[String]): Unit = {
    require(args.nonEmpty, "explicit selection required")
    Platform.defer(() => {
      val app = new StubApplication(args, StubApplication.TestsPerSuite)
      app.execute((suite, test) => println("APP_EVENT " + suite + "#" + test))
      val report = app.report
      require(report.acquired == 1 && report.released == 1)
      require(report.bodies == args.length * StubApplication.TestsPerSuite)
      println("APP_END bodies=" + report.bodies + " acquire=" + report.acquired +
        " release=" + report.released + " platform=" + Platform.name)
    })
  }
}
