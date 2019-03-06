package com.github.pshirshov.izumi.idealingua.typer2.interpreter

object Tools {

  def fail(message: String): Nothing = {
    throw new IllegalStateException(message)
  }
}
