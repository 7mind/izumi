package izumi.fundamentals.platform

package object functional {
  final type Identity[+A] = A
  final type Identity2[+E, +A] = A
  final type Identity3[-R, +E, +A] = A
}
