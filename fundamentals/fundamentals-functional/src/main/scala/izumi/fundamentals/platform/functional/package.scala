package izumi.fundamentals.platform

package object functional {
  final type Identity[+A] = A
  object Identity {
    @inline def apply[A](a: A): Identity[A] = a
  }

  final type Identity2[+E, +A] = A
  object Identity2 {
    @inline def apply[E, A](a: A): Identity2[E, A] = a
  }

  final type Identity3[-R, +E, +A] = A
  object Identity3 {
    @inline def apply[R, E, A](a: A): Identity3[R, E, A] = a
  }
}
