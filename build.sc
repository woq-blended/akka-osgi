import mill._
import mill.scalalib._
import $ivy.`de.tototec::de.tobiasroeser.mill.osgi:0.1.1`, de.tobiasroeser.mill.osgi._
import mill.scalalib.publish.{License, PomSettings, VersionControl}

object akka extends Module {

  object parsing extends OsgiBundleModule { // with PublishModule {

    val version = "10.1.11"
    val revision = "1-SNAPSHOT"
    val artifact = "akka-parsing"

//    override def pomSettings: T[PomSettings] = T{
//      PomSettings(
//        description = s"OSGi Bundle for ${artifact}",
//        organization = "de.wayofqualitiy",
//        url: ,
//        licenses: Seq[License],
//        versionControl: VersionControl,
//        developers:
//      )
//
//    }

    def publishVersion = T{ s"${version}-${revision}" }

    override def ivyDeps = T{ Agg(
      ivy"com.typesafe.akka:${artifact}_2.12:${version}"
    )}
    override def osgiHeaders: T[OsgiHeaders] = T{ super.osgiHeaders().copy(
          `Import-Package` = Seq(
            """scala.*;version="[2.12,2.12.50]""""
//            "com.sun.*;resolution:=optional",
//            "sun.*;resolution:=optional",
//            "net.liftweb.*;resolution:=optional",
//            "play.*;resolution:=optional",
//            "twirl.*;resolution:=optional",
//            "org.json4s.*;resolution:=optional",
//            "*"
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
  }

}
