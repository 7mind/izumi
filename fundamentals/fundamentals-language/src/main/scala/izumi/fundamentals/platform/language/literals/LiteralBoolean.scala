package izumi.fundamentals.platform.language.literals

import scala.annotation.unused

object LiteralBoolean {
  type Of[T <: Boolean] = T
  type Get[T <: LiteralBoolean] = T

  def apply(b: Boolean): b.type = b

  @inline final def True: true = true
  @inline final def False: false = false

  type True = true
  type False = false

  @inline def compileTimeIf[A](@unused tru: true)(ifTrue: A)(@unused ifFalse: => Any): A = ifTrue
  @inline def compileTimeIf[B](@unused fls: false)(@unused ifTrue: => Any)(ifFalse: B): B = ifFalse
}
