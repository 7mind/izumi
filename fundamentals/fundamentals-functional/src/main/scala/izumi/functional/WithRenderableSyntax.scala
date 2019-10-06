package izumi.functional

trait WithRenderableSyntax {
  def apply[T: Renderable]: Renderable[T] = implicitly

  implicit class RenderableSyntax[T: Renderable](r: T) {
    def render(): String = Renderable[T].render(r)
  }
}

