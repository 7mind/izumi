package izumi.distage.model.plan.repr

import izumi.distage.constructors.DebugProperties
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.basics.IzBoolean.{all, any}

object DIRendering {
  val colorsEnabled: Boolean = any(
    all(
      DebugProperties.`izumi.distage.rendering.colored`.boolValue(true),
      IzPlatform.terminalColorsEnabled,
    ),
    DebugProperties.`izumi.distage.rendering.colored.forced`.boolValue(false),
  )

}
