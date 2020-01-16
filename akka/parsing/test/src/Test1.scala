import org.scalatest.FreeSpec
import testsupport.ContentSpec

class Test1 extends FreeSpec {
  "test" in {
    assert(1 === 1)
  }
}

class Test2 extends ContentSpec
