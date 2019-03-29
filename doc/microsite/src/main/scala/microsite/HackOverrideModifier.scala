package microsite

import mdoc.{PostModifier, PostModifierContext}

class HackOverrideModifier extends PostModifier {
  override val name = "override"
  override def process(ctx: PostModifierContext): String = {
    val overridePrefixRegex = "HACK_OVERRIDE[0-9]*_"

    val silent = ctx.info.contains("silent")

    println(s"RUNNING OVERRIDER! silent=$silent")

    val code = if (silent) ctx.originalCode.text else ctx.outputCode

    s"""```scala
       |${code.replaceAll(overridePrefixRegex, "")}
       |```""".stripMargin
  }
}
