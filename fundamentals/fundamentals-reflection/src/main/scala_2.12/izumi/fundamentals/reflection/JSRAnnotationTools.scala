package izumi.fundamentals.reflection

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
    val maybeJSR = annos
      .collect {
        case a: u.AnnotationApi =>
          a.tree.children match {
            case (select: u.Select) :: (value: u.AssignOrNamedArgApi) :: Nil =>
              (select.children.headOption, value.lhs, value.rhs) match {
                case (Some(n: u.NewApi), i: u.IdentApi, v: u.LiteralApi) if v.value.value.isInstanceOf[String] && i.name.toString == "value" =>
                  n.children match {
                    case (head: u.TypeTreeApi) :: Nil if head.symbol.name.toString == "Named" =>
                      Some(v.value.value.asInstanceOf[String])
                    case _ =>
                      None
                  }

                case _ =>
                  None
              }
            case _ =>
              Option.empty[String]
          }
      }
      .collect {
        case Some(name) => name
      }

    maybeJSR match {
      case unique :: Nil =>
        Some(unique)
      case _ =>
        None
    }
  }
}
