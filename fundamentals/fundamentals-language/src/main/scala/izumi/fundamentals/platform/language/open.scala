package izumi.fundamentals.platform.language

import scala.annotation.StaticAnnotation

/**
  * A documenting annotation for an intentionally non-final class. In lieu of Scala 3's `open` keyword.
  *
  * @see [[https://dotty.epfl.ch/docs/reference/other-new-features/open-classes.html Open Classes]]
  */
final class open extends StaticAnnotation
