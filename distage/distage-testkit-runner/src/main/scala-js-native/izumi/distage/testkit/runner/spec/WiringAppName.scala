package izumi.distage.testkit.runner.spec

import izumi.distage.framework.CheckableApp

private[spec] object WiringAppName {
  def apply(app: CheckableApp): String = app.getClass.getName
}
