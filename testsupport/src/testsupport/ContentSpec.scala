package testsupport

import org.scalatest.FreeSpec

class ContentSpec extends FreeSpec {

  "The wrapped jar should include everything from the original" in {

    val orig : String = System.getenv("origJar")
    val osgi : String = System.getenv("osgiJar")

    println(orig)
    println(osgi)

    // both exists
//    os.isFile(orig)
//    os.isFile(osgi)
    // Todo: check jar contents
    println(s"Jar ${osgi} is OK")

    fail("In your dreams ...")
  }
}
