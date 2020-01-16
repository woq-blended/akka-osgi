import mill._
import mill.scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.1.1`
import de.tobiasroeser.mill.osgi._
import mill.api.Loose
import mill.define.Target
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

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

  override def ivyDeps = T { Agg(ivyDep) }

  override def osgiHeaders: T[OsgiHeaders] = T {
    super.osgiHeaders().copy(
      `Import-Package` = Seq(
        """scala.*;version="[2.12,2.12.50]"""",
        "*"
      )
    )
  }

  trait Tests extends super.Tests {

    override def testFrameworks: T[Seq[String]] = T {
      Seq("org.scalatest.tools.Framework")
    }

    override def ivyDeps: Target[Loose.Agg[Dep]] = T {
      Agg(
        ivy"org.scalatest::scalatest:3.0.7"
      )
    }

    override def forkEnv: Target[Map[String, String]] = T{
      super.forkEnv() ++ Map(
        "origJar" -> outer.originalJar().path.toIO.getAbsolutePath(),
        "osgiJar" -> outer.osgiBundle().path.toIO.getAbsolutePath()
      )
    }

    override def moduleDeps: Seq[JavaModule] = Seq(testsupport)

  }
}

object akka extends Module {

  object parsing extends WrapperProject {
    val version = "10.1.11"
    val revision = "1-SNAPSHOT"
    val artifact = "akka-parsing"

    override def ivyDeps = T {
      Agg(
        ivy"com.typesafe.akka::${artifact}:${version}"
      )
    }

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

object testsupport extends ScalaModule {
  override def scalaVersion = T{"2.12.10"}

  override def ivyDeps: Target[Loose.Agg[Dep]] = T {
    Agg(
      ivy"org.scalatest::scalatest:3.0.7",
      ivy"com.lihaoyi::os-lib:0.6.2"
    )
  }
}
