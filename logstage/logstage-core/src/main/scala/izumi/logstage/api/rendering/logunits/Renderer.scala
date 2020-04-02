package izumi.logstage.api.rendering.logunits

import izumi.logstage.api.Log
import izumi.logstage.api.rendering.RenderingOptions

trait Renderer {
  def render(entry: Log.Entry, context: RenderingOptions): LETree
}

object Renderer {

  class Aggregate(units: Seq[Renderer]) {
    def render(entry: Log.Entry, context: RenderingOptions): String = {
      units
        .map(u => render(context, u.render(entry, context)))
        .mkString
    }

    private def render(context: RenderingOptions, node: LETree): String = {
      node match {
        case s: LETree.Sequence =>
          s.sub.map(render(context, _)).mkString
        case s: LETree.ColoredNode =>
          val sub = render(context, s.sub)
          if (context.colored && sub.nonEmpty) {
            s"${s.color}$sub${Console.RESET}"
          } else {
            sub
          }
        case s: LETree.TextNode =>
          s.text
      }
    }

  }

}
