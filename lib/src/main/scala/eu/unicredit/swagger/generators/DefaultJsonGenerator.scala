/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package eu.unicredit.swagger.generators

import eu.unicredit.swagger.SwaggerConversion
import treehugger.forest._
import definitions._
import io.swagger.models.properties.Property
import treehuggerDSL._
import io.swagger.parser.SwaggerParser

import scala.collection.JavaConversions._

class DefaultJsonGenerator extends JsonGenerator with SwaggerConversion {

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

  def generateJsonRW(fileName: String): List[ValDef] = {
    val swagger = new SwaggerParser().read(fileName)

    val models = swagger.getDefinitions

    (for {
      (name, model) <- models
      (c, m) <- Seq(("Reads", "read"), ("Writes", "write"))
    } yield {

      val vd =
        VAL(s"$name$c", s"$c[$name]") withFlags (Flags.IMPLICIT, Flags.LAZY) := {
      c match {
        case "Reads" => ANONDEF(s"$c[$name]") := LAMBDA(PARAM("json")) ==> REF("JsSuccess") APPLY (REF(name) APPLY (
          for ((pname, prop) <- getProperties(model)) yield {
            val mtd = if (!prop.getRequired) "asOpt" else "as"

            PAREN(REF("json") INFIX ("\\", LIT(pname))) DOT mtd APPLYTYPE noOptPropType(prop)
          }
        ))
        case "Writes" => ANONDEF(s"$c[$name]") := LAMBDA(PARAM("o")) ==> REF("JsObject") APPLY (SeqClass APPLY (
          for ((pname, prop) <- getProperties(model)) yield
            LIT(pname) INFIX ("->", (REF("Json") DOT "toJson")(REF("o") DOT pname))) DOT "filter" APPLY (REF("_") DOT "_2" INFIX ("!=", REF("JsNull"))))
      }}

      vd
    }).toList
  }

  def generateJson(destPackage: String, vds: List[ValDef]): Iterable[SyntaxString] = {
    val pre = generateJsonInit(destPackage)

    val tree =
        PACKAGEOBJECTDEF("json") := BLOCK(vds)

    val code = treeToString(tree)
    Seq(SyntaxString("json", pre, code))
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString] = {
    generateJson(destPackage, generateJsonRW(fileName))
  }
}
