package izumi.distage.model.definition

sealed trait LocatorPrivacy

object LocatorPrivacy {
  /** All the bindings are public, unless explicitly marked as "confined"
    */
  case object PublicByDefault extends LocatorPrivacy
  /** All the bindings are private, unless explicitly marked as "exposed"
    */
  case object PrivateByDefault extends LocatorPrivacy

  /** Only planning roots are public
    */
  case object PublicRoots extends LocatorPrivacy

}
