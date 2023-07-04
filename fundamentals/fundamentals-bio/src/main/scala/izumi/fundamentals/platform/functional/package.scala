package izumi.fundamentals.platform

package object functional {
  final type Identity[+A] = A

  object Identity {
    def apply[A](a: A): Identity[A] = a
  }

  final type Identity2[+E, +A] = A
  final type Identity3[-R, +E, +A] = A
}
