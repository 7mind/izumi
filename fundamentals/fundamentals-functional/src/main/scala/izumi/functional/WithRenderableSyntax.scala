package izumi.functional

import izumi.functional.WithRenderableSyntax.RenderableSyntax

import scala.language.implicitConversions

trait WithRenderableSyntax {
  @inline implicit final def RenderableSyntax[T](r: T): RenderableSyntax[T] = new RenderableSyntax[T](r)
}

object WithRenderableSyntax {
  final class RenderableSyntax[T](private val r: T) extends AnyVal {
    def render()(implicit R: Renderable[T]): String = R.render(r)
  }
}
