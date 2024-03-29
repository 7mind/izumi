package izumi.distage.docker

object DockerConst {
  object Labels {
    final val reuseLabel = "distage.reuse"
    final val containerTypeLabel = "distage.type"
    final val jvmRunId = "distage.jvmrun"
    final val distageRunId = "distage.run"

    final val namePrefixLabel = "distage.name.prefix"

    final val portPrefix = "distage.port"
    final val networkDriverPrefix = "distage.driver"

    final val dependencies = "distage.dependencies"
  }

  object State {
    final val running = "running"
    final val exited = "exited"
  }

  object Vars {
    final val portPrefix = "DISTAGE_PORT"
  }
}
