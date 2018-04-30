package com.github.pshirshov.izumi.algebra

sealed trait LogFileStatus

object LogFileStatus {

  case object Current extends LogFileStatus

  case object Rotate extends LogFileStatus

  case object Filled extends LogFileStatus

  case object Pending extends LogFileStatus

}