package izumi.distage.model.definition

sealed trait LocatorPrivacy

object LocatorPrivacy {
  case object PublicByDefault extends LocatorPrivacy
  case object PrivateByDefault extends LocatorPrivacy
}
