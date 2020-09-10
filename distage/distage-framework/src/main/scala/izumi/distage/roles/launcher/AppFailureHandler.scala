package izumi.distage.roles.launcher

trait AppFailureHandler {
  def onError(t: Throwable): Unit
}

object AppFailureHandler {

  object TerminatingHandler extends AppFailureHandler {
    override def onError(t: Throwable): Unit = {
      t.printStackTrace()
      System.exit(1)
    }
  }

  object PrintingHandler extends AppFailureHandler {
    override def onError(t: Throwable): Unit = {
      t.printStackTrace()
      throw t
    }
  }

  object NullHandler extends AppFailureHandler {
    override def onError(t: Throwable): Unit = {
      throw t
    }
  }

}
