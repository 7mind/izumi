package izumi.functional

trait WithRenderableSyntax {
  def apply[T: Renderable]: Renderable[T] = implicitly[Renderable[T]]

  implicit class RenderableSyntax[T: Renderable](r: T) {
    def render(): String = implicitly[Renderable[T]].render(r)
  }
}

