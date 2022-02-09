package izumi.fundamentals.reflection

import scala.annotation.nowarn
import scala.reflect.api.Universe

object JSRAnnotationTools {

  /** This method extracts names from annotations with shape of JSR-330 Named annotation:
    *
    * <pre>
    * public @interface Named {
    * String value() default "";
    * }
    * </pre>
    *
    * The package name is being ignored
    */
  def uniqueJSRNameAnno(u: Universe)(annos: List[u.Annotation]): Option[String] = {
    @nowarn("msg=outer reference") @nowarn("msg=abstract type pattern")
    val maybeJSR = annos.collect {
      case a: u.AnnotationApi =>
        a.tree.children match {
          case (select: u.SelectApi) :: (value: u.NamedArgApi) :: Nil =>
            (select.children.headOption, value.lhs, value.rhs) match {
              case (Some(u.New(head: u.TypeTreeApi)), _: u.IdentApi, u.Literal(u.Constant(annotationArgument: String))) if {
                    head.symbol.name.toString == "Named"
                  } =>
                Some(annotationArgument)

              case _ => None
            }

          case _ =>
            None
        }
    }.flatten

    maybeJSR match {
      case unique :: Nil =>
        Some(unique)
      case _ =>
        None
    }
  }
}
