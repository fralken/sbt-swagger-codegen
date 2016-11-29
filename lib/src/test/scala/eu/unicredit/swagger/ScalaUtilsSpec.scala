package eu.unicredit.swagger

import ScalaUtils._
import org.scalatest._
import Matchers._

class ScalaUtilsSpec extends FlatSpec {

  "ScalaUtils" should "convert var identifiers" in {
    asVarId("123") shouldBe "`123`"
  }

  it should "convert plain identifiers" in {
    asPlainId("foo") shouldBe "Foo"
    asPlainId("foo-bar") shouldBe "FooBar"
  }

}
