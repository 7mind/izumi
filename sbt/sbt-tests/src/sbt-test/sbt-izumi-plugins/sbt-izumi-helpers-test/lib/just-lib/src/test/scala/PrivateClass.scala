import java.lang.Exception

class PrivateClass {
  def x() = 2+ 2
  val a = {

    // this throws a warning
    if (false) {
      val a = 2 + x()
    }

    x()
    1
  }
}
