package izumi.distage.model.exceptions

import izumi.distage.model.reflection.SafeType

class IncompatibleEffectTypesException(val provisionerEffectType: SafeType, val actionEffectType: SafeType)
  extends DIException(
    s"""Incompatible effect types: Can't execute effect in `$actionEffectType` which is neither Identity, nor a subtype of the effect that Provisioner was launched in: `$provisionerEffectType`
       |
       |Clarification:
       |  - To execute `.fromEffect` and `.fromResource` bindings for effects other than `Identity` you need to use `Injector.produceF` method with F type corresponding to the type of effects/resources you inject
       |  - Subtype type constructors are allowed. e.g. when using ZIO you can execute actions with type IO[Nothing, ?] when running in IO[Throwable, ?]
   """.stripMargin
  )
