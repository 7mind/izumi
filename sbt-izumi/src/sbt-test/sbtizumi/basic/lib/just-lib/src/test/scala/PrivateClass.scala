import java.lang.Exception

class PrivateClass {
  def x() = 2+ 2
  val a = {
    if (false) {
      println(1)
    }
    x()
    1
  }
}
