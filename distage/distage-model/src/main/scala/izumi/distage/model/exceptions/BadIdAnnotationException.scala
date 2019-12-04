package izumi.distage.model.exceptions

class BadIdAnnotationException(typeOfIdAnnotation: String, value: Any)
  extends DIException(s"Wrong annotation value, only constants are supported. Got: @$typeOfIdAnnotation($value)")
