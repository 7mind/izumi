
class LibClass extends CoreClass {
  def x: Int = sharedFunction(1)

  val a: Int = x
}
