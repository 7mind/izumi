package izumi.fundamentals.platform.jvm

trait IzJvm {

  def isHeadless: Boolean = false

  def hasColorfulTerminal: Boolean = false

  def terminalColorsEnabled: Boolean = false

}

object IzJvm extends IzJvm {
}
