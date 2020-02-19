package izumi.functional

trait WithRenderableSyntax {
  implicit class RenderableSyntax[T: Renderable](r: T) {
    def render(): String = Renderable[T].render(r)
  }
}

