package izumi.fundamentals.platform.strings

import scala.io.AnsiColor

trait IzConsoleColors {
  protected val c: AnsiColor = new AnsiColor {}
  protected def colorsEnabled(): Boolean

  protected def styled(name: String, colors: String with Singleton*): String = {
    if (colorsEnabled()) {
      s"${colors.mkString}$name${c.RESET}"
    } else {
      name
    }
  }
}
