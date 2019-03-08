//package com.github.pshirshov.izumi.idealingua
//
//import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
//import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpProjectLayout, GoProjectLayout, ScalaProjectLayout, TypeScriptProjectLayout}
//import org.scalatest.WordSpec
//
//
//class CompilerTest extends WordSpec {
//
//  import IDLTestTools._
//
//  "IDL compiler" should {
//    val id = getClass.getSimpleName
//
//    "be able to compile into scala" in {
//      require("scalac")
//      assert(compilesScala(s"$id-plain", loadDefs(), ScalaProjectLayout.PLAIN))
//      assert(compilesScala(s"$id-plain-nonportable", loadDefs("/defs/scala"), ScalaProjectLayout.PLAIN))
//    }
//
//    "be able to compile into typescript" in {
//      require("tsc", "npm", "yarn")
//      assert(compilesTypeScript(s"$id-plain", loadDefs(), TypeScriptProjectLayout.PLAIN))
//    }
//
//    "be able to compile into golang" in {
//      require("go")
//      assert(compilesGolang(s"$id-repository", loadDefs(), GoProjectLayout.REPOSITORY))
//      assert(compilesGolang(s"$id-plain", loadDefs(), GoProjectLayout.PLAIN))
//    }
//
//    "be able to compile into csharp" in {
//      require("csc", "nunit-console", "nuget", "msbuild")
//      assert(compilesCSharp(s"$id-plain", loadDefs(), CSharpProjectLayout.PLAIN))
//      assert(compilesCSharp(s"$id-nuget", loadDefs(), CSharpProjectLayout.NUGET))
//    }
//
//    "be able to compile into typescript with yarn" in {
//      // TODO: once we switch to published runtime there may be an issue with this test same as with sbt one
//      require("tsc", "npm", "yarn")
//      assert(compilesTypeScript(s"$id-yarn", loadDefs(), TypeScriptProjectLayout.YARN))
//    }
//
//    "be able to compile into scala with SBT" ignore {
//      require("sbt")
//      // we can't test sbt build: it depends on artifacts which may not exist yet
//      assert(compilesScala(s"$id-sbt", loadDefs(), ScalaProjectLayout.SBT))
//    }
//
//  }
//
//  private def require(tools: String*) = {
//    assume(IzFiles.haveExecutables(tools :_*), s"One of required tools is not available: $tools")
//  }
//}
//
