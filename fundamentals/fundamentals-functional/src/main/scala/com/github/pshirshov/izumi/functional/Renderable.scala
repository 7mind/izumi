package com.github.pshirshov.izumi.functional


trait Renderable[-T] {
  def render(value: T): String
}

object Renderable {

  implicit class RenderableSyntax[R: Renderable](r: R) {
    def render(): String = implicitly[Renderable[R]].render(r)
  }

}
