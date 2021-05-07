package microsite

import mdoc.{PostModifier, PostModifierContext}

class FakePackageModifier extends PostModifier {
  override val name = "fakepackage"
  override def process(ctx: PostModifierContext): String = {
    val regex = "\"fakepackage (.+_)\": Unit"

    val silent = ctx.info.contains("silent")

    println(s"RUNNING FAKEPACKAGE! silent=$silent")

    val code = if (silent) ctx.originalCode.text else ctx.outputCode

    s"""```scala
       |${code.replaceAll(regex, "package $1")}
       |```""".stripMargin
  }
}
