package com.github.pshirshov.izumi.fundamentals

package object graphs extends Graphs {
  private object Toposort extends Toposort

  override def toposort: Toposort = Toposort
}
