package izumi.distage.model.exceptions.macros.reflection

import izumi.distage.model.exceptions.DIException

class BadIdAnnotationException(typeOfIdAnnotation: String, value: Any)
  extends DIException(s"Wrong annotation value, only constants are supported. Got: @$typeOfIdAnnotation($value)")
