package com.github.pshirshov.izumi.models

sealed trait LogFileStatus

object LogFileStatus {
  case object Rewrite extends LogFileStatus
  case object FirstEntry extends LogFileStatus

}