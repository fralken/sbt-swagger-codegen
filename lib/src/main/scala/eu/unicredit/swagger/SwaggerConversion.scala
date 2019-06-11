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
package eu.unicredit.swagger

import io.swagger.models.Model
import io.swagger.models.properties._
import io.swagger.models.parameters._

import scala.collection.JavaConverters._
import scala.meta._

trait SwaggerConversion {

  def propType(p: Property): Type = {
    if (!p.getRequired)
      t"Option[${noOptPropType(p)}]"
    else
      noOptPropType(p)
  }

  def noOptPropType(p: Property): Type = {
    p match {
      case _: StringProperty => t"String"
      case _: BooleanProperty => t"Boolean"
      case _: DoubleProperty => t"Double"
      case _: FloatProperty => t"Float"
      case _: IntegerProperty => t"Int"
      case _: LongProperty => t"Long"
      case _: BaseIntegerProperty => t"Int"
      case m: MapProperty => t"Map[String, ${noOptPropType(m.getAdditionalProperties)}]"
      case a: ArrayProperty => t"List[${noOptPropType(a.getItems)}]"
      case _: DecimalProperty => t"BigDecimal"
      case r: RefProperty => Type.Name(r.getSimpleRef)
      case _: DateProperty => t"java.time.LocalDate"
      case _: DateTimeProperty => t"java.time.OffsetDateTime"
      case _: UUIDProperty => t"java.util.UUID"

      //case _: ByteArrayProperty => throw new Exception(s"ByteArrayProperty $p is not supported yet")
      case _: BinaryProperty => throw new Exception(s"BinaryProperty $p is not supported yet")
      // supported as a subclass of StringProperty
      //case _: EmailProperty => throw new Exception(s"EmailProperty $p is not supported yet")
      case _: FileProperty => throw new Exception(s"FileProperty $p is not supported yet")
      case _: ObjectProperty => throw new Exception(s"ObjectProperty $p is not supported yet")
      case _: PasswordProperty => throw new Exception(s"PasswordProperty $p is not supported yet")

      case null => throw new Exception("Trying to resolve null property")
      // should not happen as all existing types have been checked before
      case x => throw new Exception(s"unexpected property type $x")
    }
  }

  def paramType(p: Parameter): Type = {
    if (!p.getRequired)
      t"Option[${noOptParamType(p)}]"
    else
      noOptParamType(p)
  }

  def noOptParamType(p: Parameter): Type = {
    p match {
      case asp: AbstractSerializableParameter[_] =>
        if (asp.getType == "array")
          t"List[${noOptPropType(asp.getItems)}]"
        else
          noOptPropType(PropertyBuilder.build(asp.getType, asp.getFormat, null))
      case bp: BodyParameter =>
        noOptPropType(new RefProperty(bp.getSchema.getReference))
      case rp: RefParameter =>
        Type.Name(rp.getSimpleRef)
    }
  }

  def getProperties(model: Model): Iterable[(String, Property)] = {
    val props = model.getProperties
    if (props == null) Iterable.empty else props.asScala
  }

  def getImporter(destPackage: String, importee: String): List[Importer] = {
    List(Importer(getPackageName(destPackage), List(Importee.Name(Name(importee)))))
  }

  def getPackageName(destPackage: String): Term.Ref = {
    destPackage.split("\\.").map(Term.Name(_)).toList match {
      case a :: b :: tail => tail.foldLeft(Term.Select(a, b))(Term.Select(_, _))
      case a  => a.head
    }
  }
}
