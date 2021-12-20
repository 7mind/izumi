package izumi.distage

import scala.annotation.nowarn
import scala.reflect.macros.blackbox

final case class X[+A]() {
  def withFilter(predicate: A => Boolean): X[A] = macro withFilterImpl.apply
  def map(f: A => Unit): Nothing = ???
}
object withFilterImpl {
  def apply(c: blackbox.Context)(predicate: c.Tree): c.Tree = {
    import c.universe.*
    import scala.reflect.api.Universe

    //    val internal1 = c.universe.internal.asInstanceOf[ReificationSupport & Universe & scala.reflect.internal.Symbols]
    //    val impl      = c.universe.internal.reificationSupport.asInstanceOf[ReificationSupport#ReificationSupportImpl]
    // //    val b: impl.UnCheckIfRefutable.type = ???
    //    trait hyperblah extends internal1.ReificationSupportImpl { self: internal1.ReificationSupportImpl =>
    //      type T = self.UnCheckIfRefutable.type
    //      override lazy val UnCheckIfRefutable: T = super.UnCheckIfRefutable
    //      val T                                   = self.UnCheckIfRefutable
    // //  override def newFreeType(name: String, flags: FlagSet, origin: String): FreeTypeSymbol = ???
    //    }
    val enclosingClass = c.enclosingClass
    val positionOfMakeCall: Universe#Position = c.enclosingPosition

    assert(enclosingClass.exists(_.pos == positionOfMakeCall), "enclosingClass must contain macro call position")

    def findExprContainingMake(tree: Tree): Option[Tree] = {
      @nowarn("msg=outer reference")
      val afterLastBlock = Option {
        tree
          .filter(_.exists(_.pos == positionOfMakeCall))
          //          .filter(_.pos == positionOfMakeCall)
          .reverseIterator
          .collectFirst {
            case q"for (..$ts) yield $_" =>
              ts.collectFirst {
                case x @ fq"$_(..$_) <- $t" if t.pos == positionOfMakeCall =>
                  x
              }.head
          }.head
        //          .takeWhile { case q }
        //          .toList.head
        //          .foldLeft(null: Tree)((_, t) => t) // .last for iterator
      }
      afterLastBlock
    }

    val info = findExprContainingMake(enclosingClass) match {
      case Some(value) =>
        object inert {
          def unapply(arg: c.Tree): Option[c.Name] = arg match {
            case pq"$a @ ${pq"_"}" => Some(a)
            case _ => None
          }
        }
        value match {
          case fq"(..$inert) <- $t" =>
            s"""good
               |as=$inert
               |t=${show(t)}
               |xt=${enclosingClass.filter(_.pos == positionOfMakeCall).head}""".stripMargin
          case _ =>
            s"bad ${show(value)}"
        }
      case None => ???
    }
    c.abort(
      c.enclosingPosition,
      s"""${show(info)}}""".stripMargin,
    )

  }
}
