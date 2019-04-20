package com.github.pshirshov.izumi.distage.roles.launcher

@deprecated("migrate to new api", "2019-04-20")
sealed trait StarterState

object StarterState {

  final case object NotYetStarted extends StarterState

  final case object Starting extends StarterState

  final case object Started extends StarterState

  final case object Stopping extends StarterState

  final case object Finished extends StarterState

}
