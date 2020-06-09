import coursierapi.{Credentials, MavenRepository}

val blendedMillVersion : String = "0.3-SNAPSHOT"

interp.repositories() ++= Seq(
  MavenRepository.of(s"https://u233308-sub2.your-storagebox.de/blended-mill/$blendedMillVersion")
    .withCredentials(Credentials.of("u233308-sub2", "px8Kumv98zIzSF7k"))
)

interp.load.ivy("de.wayofquality.blended" %% "blended-mill" % blendedMillVersion)

@

// Define the Scala, Akka and Akka Http versions we want to create the bundles for
val scalaVersions : Seq[String] = Seq("2.12.11", "2.13.2")
val akkaVersions : Seq[String] = Seq("2.6.6")
val akkaHttpVersions : Seq[String] = Seq("10.1.2")

import java.nio.file.attribute.PosixFilePermission

import mill._
import mill.scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.3.0`
import de.tobiasroeser.mill.osgi._

import mill.api.{Loose, Result}
import mill.define.Segment
import mill.define.{Command, Target}
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

// imports from the blended-mill plugin
import de.wayofquality.blended.mill.versioning.GitModule
import de.wayofquality.blended.mill.publish.BlendedPublishModule
import de.wayofquality.blended.mill.modules._

val projectDir : os.Path = build.millSourcePath
val revision = T { GitSupport.publishVersion() }

object GitSupport extends GitModule {
  override def millSourcePath = projectDir
}

trait WrapperProject extends BlendedBaseModule with BlendedOsgiModule with BlendedPublishModule { outer =>

  class Deps extends BlendedDependencies
  override type ProjectDeps = Deps

  override def deps = new Deps()

  override def baseDir = projectDir

  override val githubRepo : String = "akka-osgi"
  override val scpTargetDir : String = "akka-osgi"

  override def description : String = s"OSGi Wrapper Bundle for $artifact"

  val akkaHttpVersion: String = "10.1.12"
  val akkaVersion: String = "2.6.6"

  def version: String
  def revision: String

  def artifact: String = millModuleSegments.value.flatMap {
    case Segment.Label(l) => Seq(l)
    case Segment.Cross(_) => Seq()
  }.mkString("-")

  def ivyDep = ivy"com.typesafe.akka::${artifact}:${version}"

  override def scalaVersion = T {
    "2.13.2"
  }

  override def publishVersion = T {
    s"${version}-${revision}"
  }

  override def bundleSymbolicName: T[String] = T {
    // we want the scala version as part of the bundle symbolic name
    OsgiBundleModule.calcBundleSymbolicName(pomSettings().organization, artifactId())
  }

  def originalJar: T[PathRef] = T {
    resolveDeps(T.task {
      Agg(ivyDep.exclude("*" -> "*"))
    })().toSeq.head
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
        """scala.*;version="[2.12,2.12.50]"""",
        "*"
      )
    )
  }

  def includeFromJar: T[Seq[String]] = T {
    Seq.empty[String]
  }

  override def includeResource: T[Seq[String]] = T {
    super.includeResource() ++ includeFromJar().map { f =>
      s"@${originalJar().path.toIO.getAbsolutePath()}!/$f"
    }
  }

  trait Tests extends super.BlendedTests {
    override def moduleDeps: Seq[JavaModule] = Seq(testsupport)

    override def testFrameworks: T[Seq[String]] = T {
      Seq("org.scalatest.tools.Framework")
    }

    override def ivyDeps: Target[Loose.Agg[Dep]] = T {
      super.ivyDeps() ++ Agg(deps.osLib)
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

object akka extends Module {

  object http extends WrapperProject {
    val version = akkaHttpVersion
    val revision = "1-SNAPSHOT"

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
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
        ).map(_ + s""";version="${version}"""")
      )
    }

    override def exportContents: T[Seq[String]] = T{ Seq(
      "akka.http.impl.settings",
      "akka.http.javadsl.settings",
      "akka.http.scaladsl.settings"
    )}

    override def includeFromJar: T[Seq[String]] = T { Seq(
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
    )}

    object core extends WrapperProject {
      val version = akkaHttpVersion
      val revision = "1-SNAPSHOT"

      override def osgiHeaders: T[OsgiHeaders] = T {
        super.osgiHeaders().copy(
          `Export-Package` = Seq(
            "akka.http",
            // akka.http.ccompat is a split-package (also provided by akka.parsing)
            // so we instead use `includeFromJar` and `exportContents`
            // "akka.http.ccompat",
            "akka.http.ccompat.imm",
            "akka.http.impl.*",
            "akka.http.javadsl.*",
            "akka.http.scaladsl.*",
          ).map(_ + s""";version="${version}"""")
        )
      }

      override def exportContents: T[Seq[String]] = T{ Seq(
        "akka.http.ccompat"
      )}

      override def includeFromJar: T[Seq[String]] = T { Seq(
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
      )}
    }

  }

  object parsing extends WrapperProject {
    val version = akkaHttpVersion
    val revision = "1-SNAPSHOT"

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          "akka.shapeless",
          "akka.shapeless.ops",
          "akka.shapeless.syntax",
          "akka.http.ccompat",
          "akka.parboiled2",
          "akka.parboiled2.support",
          "akka.parboiled2.util",
          "akka.macros"
        ).map(_ + s""";version="${version}"""")
      )
    }
  }

  object actor extends WrapperProject {
    val version = akkaVersion
    val revision = "1-SNAPSHOT"

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          "akka.*"
        ).map(_ + s""";version="${version}"""")
      )
    }

    override def includeFromJar: T[Seq[String]] = T { Seq(
      "reference.conf",
      "version.conf"
    )}
  }

  object protobuf extends WrapperProject {
    val version = akkaVersion
    val revision = "1-SNAPSHOT"

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          "akka.protobuf",
        ).map(_ + s""";version="${version}"""")
      )
    }
  }

  object stream extends WrapperProject {
    val version = akkaVersion
    val revision = "1-SNAPSHOT"

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          "akka.stream.*",
          "com.typesafe.sslconfig.akka.*"
        ).map(_ + s""";version="${version}"""")
      )
    }

    override def includeFromJar: T[Seq[String]] = T { Seq(
      "reference.conf"
    )}
  }
}

/** Test cases to check integrity of generated OSGi bundles. */
object testsupport extends ScalaModule {

  object Deps extends BlendedDependencies

  override def millSourcePath = projectDir / "testsupport"

  override def scalaVersion = T { scalaVersions.head }
  override def ivyDeps: Target[Loose.Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(
      Deps.scalatest,
      Deps.osLib
    )
  }
}
