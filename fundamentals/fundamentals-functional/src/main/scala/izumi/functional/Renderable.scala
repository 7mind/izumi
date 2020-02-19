package izumi.functional

trait Renderable[T]  {
  def render(value: T): String
}

object Renderable extends WithRenderableSyntax {
  @inline def apply[T: Renderable]: Renderable[T] = implicitly
}
