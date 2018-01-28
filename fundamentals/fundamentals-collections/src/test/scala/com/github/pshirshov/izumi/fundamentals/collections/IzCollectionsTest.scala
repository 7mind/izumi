
package com.github.pshirshov.izumi.fundamentals.collections

import org.scalatest.WordSpec
import IzCollections._

import scala.collection.mutable

class IzCollectionsTest extends WordSpec {

  "Collection utils" should {
    "allow to convert mappings to multimaps" in {
      assert(List("a" -> 1, "a" -> 2).toMultimap == Map("a" -> Set(1, 2)))
      assert(List("a" -> 1, "a" -> 2).toMutableMultimap == mutable.Map("a" -> mutable.Set(1, 2)))
    }
  }

  
}