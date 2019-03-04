package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.model.T2Warn

trait WarnLogger {
  def log(w: T2Warn): Unit
}
