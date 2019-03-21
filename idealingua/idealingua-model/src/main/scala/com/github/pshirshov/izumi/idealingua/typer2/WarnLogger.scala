package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn

import scala.collection.mutable

trait WarnLogger {
  def log(w: T2Warn): Unit
}

object WarnLogger {
  trait WarnLoggerImpl extends WarnLogger {
    private val warnings = mutable.ArrayBuffer.empty[T2Warn]

    override def log(w: T2Warn): Unit = {
      warnings += w
    }

    def allWarnings: Vector[T2Warn] = warnings.toVector
  }
}
