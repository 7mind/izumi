package com.github.pshirshov.izumi.functional

trait Renderable[-T] {
  def render(value: T): String
}
