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

  def generateJsonInit(packageName: String): String = {
    val initTree =
      BLOCK {
        Seq(
          IMPORT("play.api.libs.json", "_"),
          IMPORT("play.api.libs.functional.syntax", "_"))
      } inPackage packageName

    treeToString(initTree)
  }

  def generateJsonImplicits(vds: List[ValDef]): String = {
    val tree =
      PACKAGEOBJECTDEF("json") := BLOCK(vds)

    treeToString(tree)
  }

  def generateJsonRW(fileName: String): List[(String, ValDef)] = {
    val swagger = new SwaggerParser().read(fileName)
    val models = swagger.getDefinitions

    (for {
      (name, model) <- models
      (c, m) <- Seq(("Reads", "read"), ("Writes", "write"))
    } yield {

      val vd =
        VAL(s"$name$c", s"$c[$name]") withFlags (Flags.IMPLICIT, Flags.LAZY) := ({
        def mtd(prop: Property) = if (prop.getRequired) "as" else "asOpt"

        c match {
          case "Reads" =>
            NEW(ANONDEF(s"$c[$name]") := BLOCK(
              DEF(s"${m}s", s"JsResult[$name]") withFlags Flags.OVERRIDE withParams PARAM("json", "JsValue") := REF("JsSuccess") APPLY (REF(name) APPLY (
                for ((pname, prop) <- model.getProperties) yield PAREN(REF("json") INFIX ("\\", LIT(pname))) DOT mtd(prop) APPLYTYPE propType(prop, false)))))
          case "Writes" =>
            NEW(ANONDEF(s"$c[$name]") := BLOCK(
              DEF(s"${m}s", "JsValue") withFlags Flags.OVERRIDE withParams PARAM("o", name) := REF("JsObject") APPLY (SeqClass APPLY (
                for ((pname, prop) <- model.getProperties) yield LIT(pname) INFIX ("->", (REF("Json") DOT "toJson")(REF("o") DOT pname))) DOT "filter" APPLY (REF("_") DOT "_2" INFIX ("!=", REF("JsNull"))))))
        }})

      (name, vd)
    }).toList
  }

  def generateJson(destPackage: String, vds: List[(String, ValDef)]): Iterable[SyntaxString] = {
    val pre = generateJsonInit(destPackage)

    for ((name, vd) <- vds) yield {
      val tree =
        PACKAGEOBJECTDEF("json") := BLOCK(vd)

      val code = treeToString(tree)
      SyntaxString(name, pre, code)
    }
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString] = {
    generateJson(destPackage, generateJsonRW(fileName))
  }
}


