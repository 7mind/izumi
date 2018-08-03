package com.github.pshirshov.izumi.distage.model.exceptions

class BadAnnotationException(typeOfIdAnnotation: String, value: Any)
  extends DIException(s"Wrong annotation value, only constants are supporeted. Got: @$typeOfIdAnnotation($value)", null)
