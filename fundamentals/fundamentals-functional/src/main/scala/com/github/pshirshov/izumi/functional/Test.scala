package com.github.pshirshov.izumi.functional

import com.github.pshirshov.izumi.functional.maybe._
import com.github.pshirshov.izumi.functional.maybe.Result._

object Test {
  object Do {
    def apply[R](f: Safe[R]): Unit = {
      println()
      println(f)
      val result = f.run()
      println(result)

      result match {
        case Failure(t, h) => {
          h.history.foreach(v => println(s">> $v"))
        }
        case other =>

      }

    }
  }
  def main(args: Array[String]): Unit = {
    Do {
      Safe {
        2
      }.flatMap {
        v =>
          Safe {
            v + 1
          }
      }.flatMap {
        v =>
          Safe {
            v * 3
          }
      }
    }



    Do {
      Safe {
        2
      }.flatMap {
        v =>
          Safe {
            v * 3
          }
      }.flatMap {
        v =>
          Safe {
            throw new IllegalStateException()
            v + 1
          }
      }.flatMap {
        v =>
          Safe {
            v * 3
          }
      }
    }

    Do {
      Safe {
        2
      }.flatMap {
        v =>
          Safe {
            v * 3
          }
      }.flatMap {
        v =>
          Safe {
            throw new IllegalStateException()
            v + 1
          }
      }
    }


    case class Issue() extends RuntimeException with Problematic
    case class Person(name: String, age: Int)

    def parseName(input: String): Safe[String] = Safe {
      val trimmed = input.trim
      if (!trimmed.isEmpty) {
        trimmed
      } else {
        throw Issue()
      }
    }

    def parseAge(input: String): Safe[Int] = {
      try {
        val age = input.trim.toInt
        if (age >= 0) {
          Safe(age)
        } else {
          Safe.problem(new Problematic {})
        }
      }
      catch {
        case e: NumberFormatException =>
          Safe.failure(e)
      }
    }

    def parsePerson(inputName: String, inputAge: String): Safe[Person] =
      return parseName(inputName).flatMap((name: _root_.scala.Predef.String) => parseAge(inputAge).map((age: Int) => Person.apply(name, age)))

    println(parsePerson("Izumi", "17").run())
    println(parsePerson("Izumi", "-17").run())
    println(parsePerson("Izumi", "badnumber").run())

    println(parsePerson("", "17").run())
    println(parsePerson("", "-17").run())
    println(parsePerson("", "badnumber").run())

  }
}



//object Test {
//  import result._
//
//  case class Issue() extends RuntimeException() with Problematic
//
//  def main(args: Array[String]): Unit = {
//    val t1 = Safe {
//      1
//    }
//    val t2 = Safe {
//      throw new IllegalStateException(s"")
//    }
//    val t3 = Safe {
//      throw Issue()
//    }
//
//    val t4 = t1.map(_ => throw new Issue)
//
//    val t5 = Safe {
//      Safe {
//        1
//      }
//    }
//
//    val t6 = t5.flatten
//    //    val t6: Result[Result[Int, Problematic], Problematic] = Safe {
//    //      Safe {
//    //        1
//    //      }
//    //    }
//    //    val t61 = t6.flatten
//
//    val t7 = Safe {
//      Safe {
//        throw Issue()
//      }
//    }
//    val t8 = t7.flatten
//
//
//    val t9 = Safe {
//      1
//    }.flatMap {
//      v =>
//        throw new Issue()
//        Safe {
//          v
//        }
//    }
//
//    println(t1)
//    println(t2)
//    println(t3)
//    println(t4)
//    println(t5)
//    println(t6)
//    println(t7)
//    println(t8)
//    println(t9)
//
//  }
//}
