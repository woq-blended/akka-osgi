import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import coursier.core.{Organization, Resolution}
import coursier.graph.DependencyTree
import mill.Agg
import mill.define.Task
import mill.modules.Jvm
import mill.scalalib.publish.{Artifact, Dependency, Scope}

// Define the Scala, Akka and Akka Http versions we want to create the bundles for
val scalaVersions : Seq[String] = Seq("2.12.11", "2.13.2")
val akkaVersions : Seq[String] = Seq("2.6.6")
val akkaHttpVersions : Seq[String] = Seq("10.1.12")

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

/**
 * List of bundles that are currently handled by the project. These are required
 * to properly rewrite the dependencies of the published pom file.
 */
// TODO: Can we somehow autodiscover this list ?
val containedBundles : Seq[String] = Seq(
  "akka-actor", "akka-protobuf", "akka-protobuf-v3", "akka-stream", "akka-slf4j",
  "akka-http", "akka-http-core", "akka-parsing"
)

val revision = T { GitSupport.publishVersion() }

object wrapped extends mill.Cross[wrapped](scalaVersions:_*)
class wrapped(crossScalaVersion : String) extends Module {

  trait WrapperProject extends ScalaModule with OsgiBundleModule with BlendedPublishModule { outer =>

    /**
      * Settings required to publish SNAPSHOT's in a temporary Maven snapshot repo vis scp.
      */
    override val githubRepo : String = "akka-osgi"
    override val scpTargetDir : String = "akka-osgi"
    override def scpSubRepo: T[String] = T { revision() }

    /**
      * A generated description of the bundle.
      */
    override def description : String = {
      val jarRef : String = 
        bundleDeps.map(d => s"${d.dep.module.organization.value}::${d.dep.module.name.value}:${d.dep.version}")
          .mkString(",")
      s"""OSGi Wrapper Bundle for [$jarRef]. This is the original jar provided by the Akka team
         | with reviewed OSGi manifest settings. No code has been changed within the jar nor has content
         | been removed or added. For the original work, please refer to the Akka project documentation
         | at https://akka.io.""".stripMargin
    }

    override def scalaVersion : T[String] = crossScalaVersion
    def scalaBinVersion : T[String] = T { scalaVersion().split("\\.").take(2).mkString(".") }

    def typesafeVersion: String

    /**
      * The artifacts to be included in this wrapper bundle
      */
    def artifacts: Seq[String]

    def bundleDeps : Seq[Dep] = artifacts.map(a => ivy"com.typesafe.akka::${a}:${typesafeVersion}")

    override def publishVersion = T {
      s"${typesafeVersion}.${revision()}"
    }

    override def artifactName = artifacts.head

    override def bundleSymbolicName: T[String] = T {
      // we want the scala version as part of the bundle symbolic name
      OsgiBundleModule.calcBundleSymbolicName(pomSettings().organization, artifactId())
    }

    override def moduleDeps: Seq[PublishModule] = super.moduleDeps

    /** We will copy the direkt dependencies of the wrapped jar into our own pom,
     * so that we can use it as a direct drop-in in place of the original jar.
     */
    // TODO: copy more settings and from the original pom, handle copyrights etc.

    def pomDependencies : T[Agg[Dep]] = T {
      val calculateDep : coursier.Dependency => Dep = { d =>

        val modOrg : String = d.module.organization.value
        val modName : String = d.module.name.value

        val (name : String, cross : CrossVersion) = {
          if (modName.endsWith("_" + scalaBinVersion())) {
            (modName.substring(0, modName.lastIndexOf("_")), CrossVersion.Binary(false))
          } else {
            (modName, CrossVersion.empty(false))
          }
        }

        Dep(modOrg, name, d.version, cross, true)
      }

      val rewriteDep : Dep => Dep = { d =>
        if (containedBundles.contains(d.dep.module.name.value)) {
          val newVersion : String = d.dep.version + "." + revision()
          d.copy(dep = d.dep
            .withModule(d.dep.module.withOrganization(Organization(organization)))
            .withVersion(newVersion))
        } else {
          d
        }
      }

      val (flattened, res) = Lib.resolveDependenciesMetadata(
        repositories,
        resolveCoursierDependency().apply(_),
        ivyDeps() ++ transitiveIvyDeps(),
        Some(mapDependencies())
      )

      val tree = DependencyTree(res)

      val isInThisBundle : coursier.Dependency => Boolean = d =>
        artifacts.exists(a => d.module.name.value.startsWith(a + "_"))

      val pomDependencies : Seq[coursier.Dependency] =
        (tree.map(_.dependency) ++ tree.flatMap(_.children).map(_.dependency))
          .distinct
          .filter( d=> !isInThisBundle(d))

      val includedDeps : Seq[Dep] = pomDependencies.map(calculateDep).map(rewriteDep)

      Agg(includedDeps:_*)
    }

    override def publishXmlDeps: Task[Agg[Dependency]] = T.task {

      val toDependency : Dep => Dependency = { d =>
        Artifact.fromDep(d, scalaVersion(), scalaBinVersion(), "")
      }

      pomDependencies()
        .map(toDependency)
    }

    def originalJars: T[Seq[PathRef]] = T {
      resolveDeps(T.task {
        val deps : Seq[Dep] = bundleDeps.map(_.exclude("*" -> "*"))
        Agg(deps:_*)
      })().iterator.toSeq
    }

    def extraImports : T[Seq[String]] = T { Seq(
      "com.sun.*;resolution:=optional",
      "sun.*;resolution:=optional",
      "net.liftweb.*;resolution:=optional",
      "play.*;resolution:=optional",
      "twirl.*;resolution:=optional",
      "org.json4s.*;resolution:=optional"
    )}

    override def ivyDeps = T {
      Agg(bundleDeps:_*) ++ scalaLibraryIvyDeps()
    }

    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Import-Package` = Seq(
          s"""scala.util.parsing.*;version="[1.1.2,2.0.0)"""",
          s"""scala.compat.java8.*;version="[0.9,1.0.0)"""",
          s"""scala.*;version="[${scalaBinVersion()},${scalaBinVersion()}.50]""""
        ) ++ extraImports() ++ Seq("*"),
        `Export-Package` = exportPackages.map(_ + s""";version="${typesafeVersion}""""),
        `Require-Capability` = Some("""osgi.ee;filter:="(&(osgi.ee=JavaSE)(version=1.8))"""")
      )
    }

    /**
     * We put the original Manifest entries into the manifest. Otherwise the ManifestInfo.checkSameVersion
     * in akka.util may fail
     */
    override def additionalHeaders: T[Map[String, String]] = T {
      super.additionalHeaders() ++ Map(
        "Implementation-Version" -> typesafeVersion,
        "Implementation-Title" -> artifactName(),
        "Implementation-Vendor-Id" -> bundleDeps.head.dep.module.organization.value,
        "Implementation-URL" -> "https://akka.io",
        "Implementation-Vendor" -> "Lightbend"
      )
    }

    def includeFromJar: T[Seq[String]] = T {
      Seq.empty[String]
    }

    def exportPackages : Seq[String] = Seq.empty

    override def includeResource: T[Seq[String]] = T {
      super.includeResource() ++ includeFromJar().map { f =>
        s"@${originalJars().head.path.toIO.getAbsolutePath()}!/$f"
      }
    }

    trait Tests extends super.Tests {
      override def moduleDeps: Seq[JavaModule] = Seq(testsupport(crossScalaVersion))

      override def testFrameworks: T[Seq[String]] = T {
        Seq("org.scalatest.tools.Framework")
      }

      override def forkEnv: Target[Map[String, String]] = T {
        super.forkEnv() ++ Map(
          "origJars" -> outer.originalJars().map(_.path.toIO.getAbsolutePath()).mkString(":"),
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

    // object http extends HttpWrapper {

    //   override def artifact = "akka-http"

    //   override def exportPackages: Seq[String] = Seq(
    //     "akka.http.scaladsl.*",
    //     "akka.http.javadsl.*",
    //   )

    //   override def includeFromJar: T[Seq[String]] = T {
    //     Seq(
    //       "reference.conf"
    //     )
    //   }
    // }

    object api extends HttpWrapper {
      override val description : String = "Package the Akka Http API into a bundle."

      override def artifacts: Seq[String] = Seq("akka-http", "akka-http-core", "akka-parsing")

      override def exportPackages : Seq[String] = Seq(
        "akka.http.*;-split-package:=merge-first",
        "akka.macros.*",
        "akka.parboiled2.*",
        "akka.shapeless.*"
      )

      override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
        `Import-Package` = Seq(
          """scala.compat.*;version="[0.8,1)"""",
          s"""scala.*;version="[${scalaBinVersion()},${scalaBinVersion()}.50]"""",
          //"com.sun.*;resolution:=optional",
          //"sun.*;resolution:=optional",
          //"net.liftweb.*;resolution:=optional",
          //"play.*;resolution:=optional",
          //"twirl.*;resolution:=optional",
          //"org.json4s.*;resolution:=optional",
          "*"
        )
      )}
    }

    // object core extends HttpWrapper {

    //   override def artifact = "akka-http-core"

    //   override def exportPackages = Seq(
    //     """akka.http.ccompat;core="split";mandatory:="core"""",
    //     "akka.http.ccompat.imm",
    //     "akka.http.impl.*",
    //     "akka.http.javadsl.*",
    //     "akka.http.scaladsl.*",
    //     "akka.http"
    //   )

    //   override def includeFromJar: T[Seq[String]] = T {
    //     Seq(
    //       "reference.conf",
    //       "akka-http-version.conf"
    //     )
    //   }
    // }

    // object parsing extends HttpWrapper {

    //   override def artifact = "akka-parsing"

    //   override def exportPackages = Seq(
    //     """akka.http.ccompat;parsing="split";mandatory:="parsing"""",
    //     "akka.macros.*",
    //     "akka.parboiled2.*",
    //     "akka.shapeless.*"
    //   )
    // }
  }

  object akkaWrapped extends mill.Cross[akkaWrapped](akkaVersions:_*)
  class akkaWrapped(akkaVersion : String) extends Module {

    trait AkkaWrapper extends WrapperProject {
      override val typesafeVersion : String = akkaVersion
    }

    object actor extends AkkaWrapper {

      override def artifacts = Seq("akka-actor") 

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

      override def artifacts: Seq[String] = Seq("akka-protobuf")

      override def exportPackages: Seq[String] = Seq(
        "akka.protobuf",
      )
    }

    object protobufv3 extends AkkaWrapper {

      override def artifacts: Seq[String] = Seq("akka-protobuf-v3")

      override def exportPackages: Seq[String] = Seq(
        "akka.protobufv3.internal.*",
      )

      override def includeFromJar : T[Seq[String]]= T { super.includeFromJar() ++ Seq(
        "google/protobuf/any.proto",
        "google/protobuf/api.proto",
        "google/protobuf/compiler/plugin.proto",
        "google/protobuf/descriptor.proto",
        "google/protobuf/duration.proto",
        "google/protobuf/empty.proto",
        "google/protobuf/field_mask.proto",
        "google/protobuf/source_context.proto",
        "google/protobuf/struct.proto",
        "google/protobuf/timestamp.proto",
        "google/protobuf/type.proto",
        "google/protobuf/wrappers.proto"
      )}
    }

    object stream extends AkkaWrapper {

      override def artifacts : Seq[String] = Seq("akka-stream")

      override def exportPackages = Seq(
        "akka.stream.*",
        "com.typesafe.sslconfig.akka.*"
      )

      override def osgiHeaders: T[OsgiHeaders] = T { super.osgiHeaders().copy(
        `Fragment-Host` = Some(actor.bundleSymbolicName())
      )}

      override def includeFromJar: T[Seq[String]] = T {
        Seq(
          "reference.conf"
        )
      }
    }

    object slf4j extends AkkaWrapper {

      override def artifacts : Seq[String] = Seq("akka-slf4j")

      override def exportPackages = Seq(
        "akka.event.slf4j.*",
      )
    }
  }
}

/** Test cases to check integrity of generated OSGi bundles. */

object testsupport extends mill.Cross[testsupport](scalaVersions:_*)
class testsupport(crossScalaVersion : String) extends ScalaModule {

  object Deps {
    def scalatest = ivy"org.scalatest::scalatest:3.1.1"
    def osLib = ivy"com.lihaoyi::os-lib:0.6.3"
  }

  override def millSourcePath = projectDir / "testsupport"

  override def scalaVersion = T { crossScalaVersion }
  override def ivyDeps: Target[Loose.Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(
      Deps.scalatest,
      Deps.osLib
    )
  }
}
