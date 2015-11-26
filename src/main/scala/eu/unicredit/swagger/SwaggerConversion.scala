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

import treehugger.forest._
import definitions._
import treehuggerDSL._

import io.swagger.parser.SwaggerParser
import io.swagger.models.properties._
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConversions._

trait SwaggerConversion {
  def basicTypes: PartialFunction[Property, Type] = {
    case s: StringProperty =>
      StringClass
    case b: BooleanProperty =>
      BooleanClass
    case d: DoubleProperty =>
      DoubleClass
    case f: FloatProperty =>
      FloatClass
    case i: IntegerProperty =>
      IntClass
    case l: LongProperty =>
      LongClass
  }

  def paramType(p: AbstractSerializableParameter[_]): Type = {

    def complexTypes: PartialFunction[Property, Type] = {
      case a: ArrayProperty =>
        ListClass TYPE_OF baseType(p.getItems)
      case d: DecimalProperty =>
        BigDecimalClass
      case r: RefProperty =>
        RootClass.newClass(r.getSimpleRef)
      case any =>
        any match {
          case ar: AnyRef =>
            if (ar eq null)
              throw new Exception("Trying to resolve null class " + any + " for property " + any.getName)
            else
              AnyClass
          case a =>
            throw new Exception("Unmanaged primitive type " + a + " for property " + any.getName)
        }
    }

    def baseType(_p: Property): Type =
      basicTypes.orElse(complexTypes)(_p)

    val prop =
      PropertyBuilder.build(p.getType, p.getFormat, null)

    if (p.getRequired) baseType(prop)
    else OptionClass TYPE_OF baseType(prop)
  }

  def propType(p: Property, optional: Boolean): Type = {

    def complexTypes: PartialFunction[Property, Type] = {
      case m: MapProperty =>
        RootClass.newClass("Map") TYPE_OF (StringClass, baseType(m.getAdditionalProperties))
      case a: ArrayProperty =>
        ListClass TYPE_OF baseType(a.getItems)
      case d: DecimalProperty =>
        BigDecimalClass
      case r: RefProperty =>
        RootClass.newClass(r.getSimpleRef)
      case any =>
        any match {
          case ar: AnyRef =>
            if (ar eq null)
              throw new Exception("Trying to resolve null class " + any + " for property " + any.getName)
            else {
              AnyClass
            }
          case a =>
            throw new Exception("Unmanaged primitive type " + a + " for property " + any.getName)
        }
    }

    def baseType(_p: Property): Type =
      basicTypes.orElse(complexTypes)(_p)

    if (p.getRequired || !optional) baseType(p)
    else OptionClass TYPE_OF baseType(p)
  }
}