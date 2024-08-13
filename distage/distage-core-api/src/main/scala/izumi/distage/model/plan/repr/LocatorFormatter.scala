package izumi.distage.model.plan.repr

import izumi.distage.model.Locator
import izumi.functional.Renderable
import izumi.fundamentals.preamble.{toRichIterable, toRichString}

object LocatorFormatter extends Renderable[Locator] {

  override def render(value: Locator): String = {
    val minimizer = KeyMinimizer(value.allInstances.map(_.key).toSet, DIRendering.colorsEnabled)
    val kf = KeyFormatter.minimized(minimizer)
    val (priv, pub) = value.instances
      .map {
        i =>
          (i.key, s"${kf.formatKey(i.key)}")
      }.partition(v => value.isPrivate(v._1))

    Seq(
      s"Locator ${value.getClass.getName}@${Integer.toHexString(value.hashCode())} with ${value.depth} parent(s)",
      s"with exposed keys:${pub.map(_._2).niceList().shift(2)}".shift(2),
      s"with confined keys:${priv.map(_._2).niceList().shift(2)}".shift(2),
    ).mkString("\n")

  }
}
