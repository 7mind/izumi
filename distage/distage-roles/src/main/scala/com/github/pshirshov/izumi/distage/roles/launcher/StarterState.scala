package com.github.pshirshov.izumi.distage.roles.launcher

sealed trait StarterState

object StarterState {

  final case object NotYetStarted extends StarterState

  final case object Starting extends StarterState

  final case object Started extends StarterState

  final case object Stopping extends StarterState

  final case object Finished extends StarterState

}
