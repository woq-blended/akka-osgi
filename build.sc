import mill._
import mill.scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.1.1`
import de.tobiasroeser.mill.osgi._
import mill.api.Loose
import mill.define.Target
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

object akka extends Module {

  object parsing extends ScalaModule with OsgiBundleModule with PublishModule {
    val version = "10.1.11"
    val revision = "1-SNAPSHOT"
    val artifact = "akka-parsing"
    override def pomSettings: T[PomSettings] = T{
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
    override def publishVersion = T{ s"${version}-${revision}" }
    override def ivyDeps = T{ Agg(
      ivy"com.typesafe.akka::${artifact}:${version}"
    )}
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            """scala.*;version="[2.12,2.12.50]"""",
            "*"
          ),
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
        )}

    object test extends Tests {
      override def testFrameworks: T[Seq[String]] = T{Seq("org.scalatest.tools.Framework")}
      override def ivyDeps: Target[Loose.Agg[Dep]] = T{ Agg(
        ivy"org.scalatest::scalatest:3.0.7"
      )}
    }

    override def scalaVersion = "2.12.10"
  }

}
