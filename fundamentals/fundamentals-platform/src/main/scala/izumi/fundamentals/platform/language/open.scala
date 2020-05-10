package izumi.fundamentals.platform.language

import scala.annotation.StaticAnnotation

/**
  * A documentation-only annotation marking an intentionally non-final class, same purpose as Dotty's `open` keyword.
  *
  * @see motivation: https://dotty.epfl.ch/docs/reference/other-new-features/open-classes.html
  */
final class open extends StaticAnnotation
