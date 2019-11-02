package izumi.functional

trait Renderable[T]  {
  def render(value: T): String
}

object Renderable extends WithRenderableSyntax
