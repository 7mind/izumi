package izumi.distage.model.providers

import izumi.distage.model.exceptions.macros.UnsupportedDefinitionException
import izumi.distage.model.exceptions.runtime.TODOBindingException
import izumi.distage.model.reflection.Provider.{ErrorMakeWithoutFromMarker, ProviderType}
import izumi.distage.model.reflection.{DIKey, Provider, SafeType}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.Tag

trait SimpleDistageFunctoids {
  def todoProvider(key: DIKey)(implicit pos: CodePositionMaterializer): Functoid[Nothing] = {
    Functoid.create[Nothing](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = key.tpe,
        fun = _ => throw new TODOBindingException(s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.get}!", key, pos),
        providerType = ProviderType.Function,
      )
    )
  }

  /**
    * Marker provider used as the placeholder for a `make[T]` binding that has not been completed with
    * a follow-up `.from`-like call.
    *
    * `PlanVerifier` (and `PlanCheck`) detects bindings whose impl carries this provider type and fails
    * verification with a deprecation message. If the user actually executes such a binding at runtime
    * (which can happen if `PlanVerifier` is bypassed), the `fun` of this provider throws
    * [[UnsupportedDefinitionException]] with the same message.
    */
  def _errorMakeWithoutFrom[T: Tag](tpeStr: String, nonWhitelistedMethods: List[String]): Functoid[T] = {
    val message = SimpleDistageFunctoids.errorMakeWithoutFromMessage(tpeStr, nonWhitelistedMethods)
    Functoid.create[T](
      Provider.ProviderImpl(
        parameters = Seq.empty,
        ret = SafeType.get[T],
        underlying = ErrorMakeWithoutFromMarker(tpeStr, nonWhitelistedMethods, message),
        fun = (_: Seq[Any]) => throw new UnsupportedDefinitionException(message),
        providerType = ProviderType.ErrorMakeWithoutFrom,
      )
    )
  }
}

object SimpleDistageFunctoids {
  def errorMakeWithoutFromMessage(tpeStr: String, nonWhitelistedMethods: List[String]): String = {
    if (nonWhitelistedMethods.isEmpty) {
      s"""`make[$tpeStr]` DSL deprecation: `make[$tpeStr]` without a following `.from`-like call is deprecated and will fail at runtime in a future version.
         |Use `make[$tpeStr].fromSelf` (equivalent to `make[$tpeStr].from[$tpeStr]`) to keep auto-deriving the constructor for $tpeStr,
         |or pick a different `.from`/`.fromValue`/`.fromResource`/`.using`/`.todo` binding.
         |""".stripMargin
    } else {
      s"""`make[$tpeStr]` DSL failure: constructor for $tpeStr WAS NOT generated.
         |After `make[$tpeStr]` the following method calls were detected in the same expression:${nonWhitelistedMethods.niceList()}
         |
         |These calls were treated as `.from`-like (they are not in the allowed no-op list).
         |The DSL expects every such chain to eventually call one of `.from`/`.fromValue`/`.fromResource`/`.using`/`.todo`/`.fromSelf`/`.fromEffect` to fill in the constructor — none of those was found.
         |
         |If you intended to bind $tpeStr to its own auto-derived constructor, use `make[$tpeStr].fromSelf`.
         |""".stripMargin
    }
  }
}
