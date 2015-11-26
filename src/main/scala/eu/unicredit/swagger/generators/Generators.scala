package eu.unicredit.swagger.generators

trait ModelGenerator {

  /*
  Let say:
  returns a String "Header" and a list of String one per Model class
  */
  def generate(): (String, Seq[String])

}

class DefaultModelGenerator extends ModelGenerator {

  def generate(): (String, Seq[String]) = {
    println("Called default model generator genarate method!!!")
    ("", Seq())
  }
}

class AlternativeModelGenerator extends ModelGenerator {

  def generate(): (String, Seq[String]) = {
    println("Called ALTERNATIVE model generator genarate method!!!")
    ("", Seq())
  }
}
