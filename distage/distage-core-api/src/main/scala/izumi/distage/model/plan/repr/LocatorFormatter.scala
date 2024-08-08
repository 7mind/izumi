package izumi.distage.model.plan.repr

import izumi.distage.model.Locator
import izumi.functional.Renderable
import izumi.fundamentals.preamble.{toRichIterable, toRichString}

object LocatorFormatter extends Renderable[Locator] {

  override def render(value: Locator): String = {
    val minimizer = KeyMinimizer(value.allInstances.map(_.key).toSet, DIRendering.colorsEnabled)
    val kf = KeyFormatter.minimized(minimizer)
    val (priv, pub) = value.instances.map(_.key).partition(value.isPrivate)

    Seq(
      s"Locator ${super.toString}",
      s"with exposed keys:${pub.map(kf.formatKey).niceList().shift(2)}".shift(2),
      s"with confined keys:${priv.map(kf.formatKey).niceList().shift(2)}".shift(2),
    ).mkString("\n")

  }
}
