import mill._
import mill.scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.1.1`
import de.tobiasroeser.mill.osgi._
import mill.api.{Loose,Result}
import mill.define.Target
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

object Deps {
  val osLib = ivy"com.lihaoyi::os-lib:0.6.3"
  val scalatest = ivy"org.scalatest::scalatest:3.0.7"
}

trait WrapperProject extends ScalaModule with OsgiBundleModule with PublishModule { outer =>

  def version: String
  def revision: String
  def artifact: String
  def ivyDep = ivy"com.typesafe.akka::${artifact}:${version}"

  override def scalaVersion = T{ "2.12.10" }

  override def publishVersion = T {
    s"${version}-${revision}"
  }

  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = s"OSGi Wrapper Bundle for ${artifact}",
      organization = "de.wayofqualitiy",
      url = "https://github.com/woq-blended/akka-osgi",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github(owner = "woq-blended", repo = "akka-osgi"),
      developers = Seq(
        Developer(id = "lefou", name = "Tobias Roeser", url = "https://github.com/lefou"),
        Developer(id = "atooni", name = "Andreas Gies", url = "https://github.com/atooni")
      )
    )
  }

  def originalJar: T[PathRef] = T{
    resolveDeps(T.task{ Agg(ivyDep.exclude("*" -> "*"))})().toSeq.head
  }

  override def ivyDeps = T { scalaLibraryIvyDeps() }
  override def compileIvyDeps = T { Agg(ivyDep) }

  override def osgiHeaders: T[OsgiHeaders] = T {
    super.osgiHeaders().copy(
      `Import-Package` = Seq(
        """scala.*;version="[2.12,2.12.50]"""",
        "*"
      )
    )
  }

  def compileIvyDepsTree(inverse: Boolean = false) = T.command {
    val (flattened, resolution) = Lib.resolveDependenciesMetadata(
      repositories,
      resolveCoursierDependency().apply(_),
      compileIvyDeps() ++ transitiveIvyDeps(),
      Some(mapDependencies())
    )

    println(
      coursier.util.Print.dependencyTree(
        roots = flattened,
        resolution = resolution,
        printExclusions = false,
        reverse = inverse
      )
    )

    Result.Success()
  }

  trait Tests extends super.Tests {
    override def moduleDeps: Seq[JavaModule] = Seq(testsupport)
    override def testFrameworks: T[Seq[String]] = T { Seq("org.scalatest.tools.Framework") }
    override def ivyDeps: Target[Loose.Agg[Dep]] = T { Agg(Deps.scalatest) }
    override def forkEnv: Target[Map[String, String]] = T{
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
}

object akka extends Module {

  object `httpCore` extends WrapperProject {
    val version = "10.1.11"
    val revision = "1-SNAPSHOT"
    val artifact = "akka-http-core"
    override def osgiHeaders: T[OsgiHeaders] = T {
      super.osgiHeaders().copy(
        `Export-Package` = Seq(
          "akka.http",
          "akka.http.ccompat",
          "akka.http.ccompat.imm",
          "akka.http.impl.*",
          "akka.http.javadsl.*",
          "akka.http.scaladsl.*",
        ).map(_ + s""";version="${version}"""")
      )
    }
    object test extends Tests
  }

  object parsing extends WrapperProject {
    val version = "10.1.11"
    val revision = "1-SNAPSHOT"
    val artifact = "akka-parsing"
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
    object test extends Tests
  }

}

/** Test cases to check integrity of generated OSGi bundles. */
object testsupport extends ScalaModule {
  override def scalaVersion = T{"2.12.10"}
  override def ivyDeps: Target[Loose.Agg[Dep]] = T {
    Agg(
      Deps.scalatest,
      Deps.osLib
    )
  }
}

/** Generate IntelliJ IDEA project files. */
def idea(ev: mill.eval.Evaluator) = T.command {
  GenIdeaImpl(
    ev,
    implicitly,
    ev.rootModule,
    ev.rootModule.millDiscover
  ).run()
}
