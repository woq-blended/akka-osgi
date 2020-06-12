import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import mill.Agg

// Define the Scala, Akka and Akka Http versions we want to create the bundles for
val scalaVersions : Seq[String] = Seq("2.12.11", "2.13.2")
val akkaVersions : Seq[String] = Seq("2.6.5", "2.6.6")
val akkaHttpVersions : Seq[String] = Seq("10.1.11", "10.1.12")

import mill._
import mill.scalalib._

import mill.api.{Loose, Result}
import mill.define.Segment
import mill.define.{Command, Target}
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.3.0`
import de.tobiasroeser.mill.osgi._

import $file.GitModule
import GitModule.GitModule

import $file.Publish
import Publish.BlendedPublishModule

val projectDir : os.Path = build.millSourcePath

object GitSupport extends GitModule {
  override def millSourcePath = projectDir
}

object Deps {
  def scalatest = ivy"org.scalatest::scalatest:3.1.1"
  def osLib = ivy"com.lihaoyi::os-lib:0.6.3"
}

val revision = T { GitSupport.publishVersion() }

object wrapped extends mill.Cross[wrapped](scalaVersions:_*)
class wrapped(crossScalaVersion : String) extends Module {

  trait WrapperProject extends ScalaModule with OsgiBundleModule with BlendedPublishModule { outer =>

    override val githubRepo : String = "akka-osgi"
    override val scpTargetDir : String = "akka-osgi"

    override def description : String = s"OSGi Wrapper Bundle for $artifact"
    override def scalaVersion : T[String] = crossScalaVersion
    def scalaBinVersion : T[String] = T { scalaVersion().split("\\.").take(2).mkString(".") }

    def typesafeVersion: String
    def artifact: String

    def ivyDep = ivy"com.typesafe.akka::${artifact}:${typesafeVersion}"

    override def publishVersion = T {
      s"${typesafeVersion}-${revision()}"
    }

    override def artifactName = artifact

    override def bundleSymbolicName: T[String] = T {
      // we want the scala version as part of the bundle symbolic name
      OsgiBundleModule.calcBundleSymbolicName(pomSettings().organization, artifactId())
    }

    def originalJar: T[PathRef] = T {
      resolveDeps(T.task {
        Agg(ivyDep.exclude("*" -> "*"))
      })().iterator.toSeq.head
    }

    override def ivyDeps = T {
      scalaLibraryIvyDeps()
    }

    override def compileIvyDeps = T {
      Agg(ivyDep)
    }

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Import-Package` = Seq(
          s"""scala.*;version="[${scalaBinVersion()},${scalaBinVersion()}.50]"""",
          "*"
        ),
        `Export-Package` = exportPackages.map(_ + s""";version="${typesafeVersion}"""")
      )
    }

    def includeFromJar: T[Seq[String]] = T {
      Seq.empty[String]
    }

    def exportPackages : Seq[String] = Seq.empty

    override def includeResource: T[Seq[String]] = T {
      super.includeResource() ++ includeFromJar().map { f =>
        s"@${originalJar().path.toIO.getAbsolutePath()}!/$f"
      }
    }

    trait Tests extends super.Tests {
      override def moduleDeps: Seq[JavaModule] = Seq(testsupport(crossScalaVersion))

      override def testFrameworks: T[Seq[String]] = T {
        Seq("org.scalatest.tools.Framework")
      }

      override def ivyDeps: Target[Loose.Agg[Dep]] = T {
        super.ivyDeps() ++ Agg(Deps.osLib)
      }

      override def forkEnv: Target[Map[String, String]] = T {
        super.forkEnv() ++ Map(
          "origJar" -> outer.originalJar().path.toIO.getAbsolutePath(),
          "osgiJar" -> outer.osgiBundle().path.toIO.getAbsolutePath()
        )
      }

      override def generatedSources: Target[Seq[PathRef]] = T {
        val src =
          """
            |class ContentSpec extends testsupport.ContentSpec
            |""".stripMargin
        os.write(T.ctx.dest / "specs.scala", src)
        super.generatedSources() ++ Seq(PathRef(T.ctx.dest))
      }
    }

    // enforce the test for each wrapper project
    object test extends Tests
  }

  object httpWrapped extends mill.Cross[httpWrapped](akkaHttpVersions:_*)
  class httpWrapped(akkaHttpVersion : String) extends Module {

    trait HttpWrapper extends WrapperProject {
      override val typesafeVersion : String = akkaHttpVersion
    }

    object http extends HttpWrapper {

      override def artifact = "akka-http"

      override def exportPackages: Seq[String] = Seq(
        "akka.http.impl.settings.engine",
        "akka.http.impl.settings.model",
        "akka.http.impl.settings.util",
        "akka.http.javadsl.coding",
        "akka.http.javadsl.common",
        "akka.http.javadsl.marshalling.*",
        "akka.http.javadsl.server.*",
        "akka.http.javadsl.unmarshalling.*",
        "akka.http.scaladsl.client",
        "akka.http.scaladsl.coding",
        "akka.http.scaladsl.common",
        "akka.http.scaladsl.marshalling.*",
        "akka.http.scaladsl.server.*",
        "akka.http.scaladsl.unmarshalling.*"
      )

      override def exportContents: T[Seq[String]] = T {
        Seq(
          "akka.http.impl.settings",
          "akka.http.javadsl.settings",
          "akka.http.scaladsl.settings"
        )
      }

      override def includeFromJar: T[Seq[String]] = T {
        Seq(
          "reference.conf",
          "akka/http/javadsl/settings/ServerSentEventSettings.class",
          "akka/http/javadsl/settings/RoutingSettings.class",
          "akka/http/javadsl/settings/RoutingSettings$.class",
          "akka/http/scaladsl/settings/ServerSentEventSettings.class",
          "akka/http/scaladsl/settings/ServerSentEventSettings$.class",
          "akka/http/scaladsl/settings/RoutingSettings.class",
          "akka/http/scaladsl/settings/RoutingSettings$.class",
          "akka/http/impl/settings/ServerSentEventSettingsImpl.class",
          "akka/http/impl/settings/RoutingSettingsImpl.class",
          "akka/http/impl/settings/RoutingSettingsImpl$.class",
          "akka/http/impl/settings/ServerSentEventSettingsImpl$.class"
        )
      }
    }

    object core extends HttpWrapper {

      override def artifact = "akka-http-core"

      override def exportPackages = Seq(
        "akka.http",
        // akka.http.ccompat is a split-package (also provided by akka.parsing)
        // so we instead use `includeFromJar` and `exportContents`
        // "akka.http.ccompat",
        "akka.http.ccompat.imm",
        "akka.http.impl.*",
        "akka.http.javadsl.*",
        "akka.http.scaladsl.*",
      )

      override def exportContents: T[Seq[String]] = T {
        Seq(
          "akka.http.ccompat"
        )
      }

      override def includeFromJar: T[Seq[String]] = T {
        Seq(
          "reference.conf",
          "akka-http-version.conf",
          "akka/http/ccompat/CompatImpl.class",
          "akka/http/ccompat/package$.class",
          "akka/http/ccompat/MapHelpers$.class",
          "akka/http/ccompat/CompatImpl$.class",
          "akka/http/ccompat/Builder.class",
          "akka/http/ccompat/package.class",
          "akka/http/ccompat/QuerySeqOptimized.class",
          "akka/http/ccompat/CompatImpl$$anon$1.class",
          "akka/http/ccompat/MapHelpers.class"
        )
      }
    }

    object parsing extends HttpWrapper {

      override def artifact = "akka-parsing"

      override def exportPackages = Seq(
        "akka.shapeless",
        "akka.shapeless.ops",
        "akka.shapeless.syntax",
        "akka.http.ccompat",
        "akka.parboiled2",
        "akka.parboiled2.support",
        "akka.parboiled2.util",
        "akka.macros"
      )
    }
  }

  object akkaWrapped extends mill.Cross[akkaWrapped](akkaVersions:_*)
  class akkaWrapped(akkaVersion : String) extends Module {

    trait AkkaWrapper extends WrapperProject {
      override val typesafeVersion : String = akkaVersion
    }

    object actor extends AkkaWrapper {

      override def artifact = "akka-actor"

      override def exportPackages = Seq(
        "akka.*"
      )

      override def includeFromJar: T[Seq[String]] = T {
        Seq(
          "reference.conf",
          "version.conf"
        )
      }
    }

    object protobuf extends AkkaWrapper {

      override def artifact: String = "akka-protobuf"

      override def exportPackages: Seq[String] = Seq(
        "akka.protobuf",
      )
    }

    object stream extends AkkaWrapper {

      override def artifact = "akka-stream"

      override def exportPackages = Seq(
        "akka.stream.*",
        "com.typesafe.sslconfig.akka.*"
      )

      override def includeFromJar: T[Seq[String]] = T {
        Seq(
          "reference.conf"
        )
      }
    }
  }
}

/** Test cases to check integrity of generated OSGi bundles. */

object testsupport extends mill.Cross[testsupport](scalaVersions:_*)
class testsupport(crossScalaVersion : String) extends ScalaModule {

  override def millSourcePath = projectDir / "testsupport"

  override def scalaVersion = T { crossScalaVersion }
  override def ivyDeps: Target[Loose.Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(
      Deps.scalatest,
      Deps.osLib
    )
  }
}
