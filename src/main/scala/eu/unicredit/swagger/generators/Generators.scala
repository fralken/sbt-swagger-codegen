package eu.unicredit.swagger.generators

import eu.unicredit.swagger.SwaggerConversion

case class SyntaxString(name: String, pre: String, code: String)

trait Generator {
}

trait ModelGenerator extends Generator {

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString]

}

trait JsonGenerator extends Generator {

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString]

}

object DefaultModelGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq()
}

class DefaultModelGenerator extends ModelGenerator with SwaggerConversion {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._
  import io.swagger.parser.SwaggerParser
  import io.swagger.models.properties._
  import scala.collection.JavaConversions._

  def generateClass(name: String, props: Iterable[(String, Property)], comments: Option[String]): String = {
    val GenClass = RootClass.newClass(name)

    val params: Iterable[ValDef] = for ((pname, prop) <- props) yield PARAM(pname, propType(prop, true)): ValDef

    val tree: Tree = CLASSDEF(GenClass) withFlags Flags.CASE withParams params

    val resTree =
      comments.map(tree withComment _).getOrElse(tree)

    treeToString(resTree)
  }

  def generateModelInit(packageName: String): String = {
    //val initTree =
      //PACKAGE(packageName)

    //treeToString(initTree)
    "package "+packageName
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString] = {
    val swagger = new SwaggerParser().read(fileName)
    val models = swagger.getDefinitions

    val modelss =
      for {
        (name, model) <- models
        description = model.getDescription
        properties = model.getProperties
      } yield SyntaxString(name, generateModelInit(destPackage), generateClass(name, properties, Option(description)))

    modelss
  }
}

object DefaultJsonGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq()
}


class DefaultJsonGenerator extends JsonGenerator with SwaggerConversion {
  import treehugger.forest._
  import definitions._
  import treehuggerDSL._
  import io.swagger.parser.SwaggerParser
  import io.swagger.models.properties._
  import scala.collection.JavaConversions._

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString] = {
    throw new Exception("TBD")
  }
}


