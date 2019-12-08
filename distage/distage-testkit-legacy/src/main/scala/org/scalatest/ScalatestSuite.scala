package org.scalatest

object ScalatestSuite {
  def suiteToString(substitution: Option[(String, String)], theSuite: Suite): String = Suite.suiteToString(substitution, theSuite)
}

