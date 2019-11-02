package izumi.distage.model.references

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

final case class IdentifiedRef(key: DIKey, value: Any)
