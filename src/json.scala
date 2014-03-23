/**********************************************************************************************\
* Rapture JSON Library                                                                         *
* Version 0.9.0                                                                                *
*                                                                                              *
* The primary distribution site is                                                             *
*                                                                                              *
*   http://rapture.io/                                                                         *
*                                                                                              *
* Copyright 2010-2014 Jon Pretty, Propensive Ltd.                                              *
*                                                                                              *
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file    *
* except in compliance with the License. You may obtain a copy of the License at               *
*                                                                                              *
*   http://www.apache.org/licenses/LICENSE-2.0                                                 *
*                                                                                              *
* Unless required by applicable law or agreed to in writing, software distributed under the    *
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
* either express or implied. See the License for the specific language governing permissions   *
* and limitations under the License.                                                           *
\**********************************************************************************************/
package rapture.json

import rapture.core._

import scala.collection.mutable.{ListBuffer, HashMap}

import language.dynamics

object JsonBuffer {
  def parse[Source: JsonBufferParser](s: Source)(implicit eh: ExceptionHandler):
      eh.![JsonBuffer, ParseException] = eh.wrap {
    new JsonBuffer(Jsonized(try implicitly[JsonParser[Source]].parse(s).get catch {
      case e: NoSuchElementException => throw new ParseException(s.toString)
    }))
  }
  
  def apply[T: Jsonizer](t: T)(implicit parser: JsonBufferParser[_]): JsonBuffer =
    new JsonBuffer(Jsonized(implicitly[Jsonizer[T]].jsonize(t)))

}

/** Companion object to the `Json` type, providing factory and extractor methods, and a JSON
  * pretty printer. */
object Json {

  /** Parses a string containing JSON into a `Json` object */
  def parse[Source: JsonParser](s: Source)(implicit eh: ExceptionHandler):
      eh.![Json, ParseException] = eh.wrap {
    new Json(try implicitly[JsonParser[Source]].parse(s).get catch {
      case e: NoSuchElementException => throw new ParseException(s.toString)
    })
  }

  def apply[T: Jsonizer](t: T)(implicit parser: JsonParser[_]) =
    new Json(implicitly[Jsonizer[T]].jsonize(t))

  def wrapDynamic(any: Any)(implicit parser: JsonParser[_]) = new Json(any)

  def extractDynamic(json: Json) = json.json

  def unapply(json: Any)(implicit parser: JsonParser[_]): Option[Json] = Some(new Json(json))

  def format(json: Json): String = format(Some(json.json), 0, json.parser)
  
  /** Formats the JSON object for multi-line readability. */
  def format(json: Option[Any], ln: Int, parser: JsonParser[_], pad: String = " ",
      brk: String = "\n"): String = {
    val indent = " "*ln
    json match {
      case None => "null"
      case Some(j) =>
        if(parser.isString(j)) {
          "\""+parser.getString(j).replaceAll("\\\\", "\\\\\\\\").replaceAll("\r",
              "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"")+"\""
        } else if(parser.isBoolean(j)) {
          if(parser.getBoolean(j)) "true" else "false"
        } else if(parser.isNumber(j)) {
          val n = parser.getDouble(j)
          if(n == n.floor) n.toInt.toString else n.toString
        } else if(parser.isArray(j)) {
          List("[", parser.getArray(j) map { v =>
            s"${indent}${pad}${format(Some(v), ln + 1, parser)}"
          } mkString s",${brk}", s"${indent}]") mkString brk
        } else if(parser.isObject(j)) {
          List("{", parser.getKeys(j) map { k =>
            val inner = try Some(parser.dereferenceObject(j, k)) catch {
              case e: Exception => None
            }
            s"""${indent}${pad}"${k}":${pad}${format(inner, ln + 1, parser)}"""
          } mkString s",${brk}", s"${indent}}") mkString brk
        } else if(parser.isNull(j)) "null"
        else "undefined"
    }
  }

  def serialize(json: Json): String = format(Some(json.normalize), 0, json.parser, "", "")
  
}

/** Represents some parsed JSON. */
class Json(val json: Any, path: Vector[Either[Int, String]] = Vector())(implicit
    val parser: JsonParser[_]) extends Dynamic {

  def $accessInnerJsonMap(k: String): Any = parser.dereferenceObject(json, k)

  override def equals(any: Any) = any match {
    case any: Json => json == any.json
    case _ => false
  }

  override def hashCode = json.hashCode & "json".hashCode

  /** Assumes the Json object is wrapping a List, and extracts the `i`th element from the
    * vector */
  def apply(i: Int): Json =
    new Json(json, Left(i) +: path)
 
  /** Combines a `selectDynamic` and an `apply`.  This is necessary due to the way dynamic
    * application is expanded. */
  def applyDynamic(key: String)(i: Int): Json = selectDynamic(key).apply(i)
  
  /** Navigates the JSON using the `Vector[String]` parameter, and returns the element at that
    * position in the tree. */
  def extract(sp: Vector[String]): Json =
    if(sp.isEmpty) this else selectDynamic(sp.head).extract(sp.tail)
  
  /** Assumes the Json object wraps a `Map`, and extracts the element `key`. */
  def selectDynamic(key: String): Json =
    new Json(json, Right(key) +: path)
 
  private[json] def normalize: Any =
    yCombinator[(Any, Vector[Either[Int, String]]), Any] { fn => _ match {
      case (j, Vector()) => j: Any
      case (j, t :+ Right(k)) =>
        fn({
          if(parser.isObject(j)) {
            try parser.dereferenceObject(j, k) catch {
              case TypeMismatchException(f, e, _) =>
                TypeMismatchException(f, e, path.drop(t.length))
              case e: Exception =>
                throw MissingValueException(path.drop(t.length))
            }
          } else {
            throw TypeMismatchException(parser.getType(j), JsonTypes.Object,
              path.drop(t.length))
          }
        }, t)
      case (j, t :+ Left(i)) =>
        fn((
          if(parser.isArray(j)) {
            try parser.dereferenceArray(j, i) catch {
              case TypeMismatchException(f, e, _) =>
                TypeMismatchException(f, e, path.drop(t.length))
              case e: Exception =>
                throw MissingValueException(path.drop(t.length))
            }
          } else {
            throw TypeMismatchException(parser.getType(j), JsonTypes.Array, path.drop(t.length))
          }
        , t))
    } } (json -> path)

  /** Assumes the Json object is wrapping a `T`, and casts (intelligently) to that type. */
  def get[T](implicit eh: ExceptionHandler, ext: Extractor[T]): eh.![T, JsonGetException] =
    eh.wrap(try ext.rawConstruct(normalize, parser) catch {
      case TypeMismatchException(f, e, _) => throw TypeMismatchException(f, e, path)
      case e: MissingValueException => throw e
    })

  override def toString =
    try Json.format(Some(normalize), 0, parser) catch {
      case e: JsonGetException => "undefined"
    }
}

object Jsonized {
  implicit def toJsonized[T: Jsonizer](t: T) = Jsonized(implicitly[Jsonizer[T]].jsonize(t))
}
case class Jsonized(private[json] var value: Any)

class JsonBuffer(private[json] var json: Jsonized, path: Vector[Either[Int, String]] = Vector())
    (implicit val parser: JsonBufferParser[_]) extends Dynamic {
  /** Updates the element `key` of the JSON object with the value `v` */
  def updateDynamic(key: String)(v: Jsonized): Unit =
    updateParents(path, parser.setObjectValue(normalizeOrEmpty, key, v.value))
 
  /** Updates the `i`th element of the JSON array with the value `v` */
  def update[T: Jsonizer](i: Int, v: T): Unit =
    updateParents(path, parser.setArrayValue(normalize, i, implicitly[Jsonizer[T]].jsonize(v)))

  protected def updateParents(path: Vector[Either[Int, String]], newVal: Any): Unit =
    path match {
      case Vector() =>
        json.value = newVal
      case init :+ Left(idx) =>
        val jb = new JsonBuffer(json, init)
        updateParents(init, parser.setArrayValue(jb.normalizeOrNil, idx, newVal))
      case init :+ Right(key) =>
        val jb = new JsonBuffer(json, init)
        updateParents(init, parser.setObjectValue(jb.normalizeOrEmpty, key, newVal))
    }

  /** Removes the specified key from the JSON object */
  def -=(k: String): Unit = updateParents(path, parser.removeObjectValue(normalize, k))

  /** Adds the specified value to the JSON array */
  def +=[T: Jsonizer](v: T): Unit =
    updateParents(path, parser.addArrayValue(normalize, implicitly[Jsonizer[T]].jsonize(v)))

  def apply(i: Int): JsonBuffer = new JsonBuffer(json, Left(i) +: path)
 
  /** Combines a `selectDynamic` and an `apply`.  This is necessary due to the way dynamic
    * application is expanded. */
  def applyDynamic(key: String)(i: Int): JsonBuffer = selectDynamic(key).apply(i)
  
  /** Navigates the JSON using the `List[String]` parameter, and returns the element at that
    * position in the tree. */
  def extract(sp: Vector[String]): JsonBuffer =
    if(sp.isEmpty) this else selectDynamic(sp.head).extract(sp.tail)
  
  /** Assumes the Json object wraps a `Map`, and extracts the element `key`. */
  def selectDynamic(key: String): JsonBuffer =
    new JsonBuffer(json, Right(key) +: path)

  private[json] def normalizeOrNil: Any =
    try normalize catch { case e: Exception => parser.fromArray(List()) }

  private[json] def normalizeOrEmpty: Any =
    try normalize catch { case e: Exception => parser.fromObject(Map()) }

  private[json] def normalize: Any =
    yCombinator[(Any, Vector[Either[Int, String]]), Any] { fn => _ match {
      case (j, Vector()) => j: Any
      case (j, t :+ Right(k)) =>
        fn({
          if(parser.isObject(j)) {
            try parser.dereferenceObject(j, k) catch {
              case TypeMismatchException(f, e, _) =>
                TypeMismatchException(f, e, path.drop(t.length))
              case e: Exception =>
                throw MissingValueException(path.drop(t.length))
            }
          } else {
            throw TypeMismatchException(parser.getType(j), JsonTypes.Object,
              path.drop(t.length))
          }
        }, t)
      case (j, t :+ Left(i)) =>
        fn((
          if(parser.isArray(j)) {
            try parser.dereferenceArray(j, i) catch {
              case TypeMismatchException(f, e, _) =>
                TypeMismatchException(f, e, path.drop(t.length))
              case e: Exception =>
                throw MissingValueException(path.drop(t.length))
            }
          } else {
            throw TypeMismatchException(parser.getType(j), JsonTypes.Array, path.drop(t.length))
          }
        , t))
    } } (json.value -> path)

  /** Assumes the Json object is wrapping a `T`, and casts (intelligently) to that type. */
  def get[T](implicit ext: Extractor[T], eh: ExceptionHandler): eh.![T, JsonGetException] =
    eh wrap {
      try ext.rawConstruct(normalize, parser) catch {
        case TypeMismatchException(f, e, _) => throw TypeMismatchException(f, e, path)
        case e: MissingValueException => throw e
      }
    }

  override def toString =
    try Json.format(Some(normalize), 0, parser) catch {
      case e: JsonGetException => "undefined"
    }
}

class AnyExtractor[T](cast: Json => T) extends BasicExtractor[T](x => cast(x))

case class BasicExtractor[T](val cast: Json => T) extends Extractor[T] {
  def construct(js: Json) = cast(js)
}

case class CascadeExtractor[T](casts: (Json => T)*) extends Extractor[T] {
  def construct(js: Json) = {
    (casts.foldLeft(None: Option[T]) { case (v, next) =>
      v orElse { try Some(next(js)) catch { case e: Exception => None } }
    }).get
  }
}

object JsonGetException {
  def stringifyPath(path: Vector[Either[Int, String]]) = path.reverse map {
    case Left(i) => s"($i)"
    case Right(s) => s".$s"
  } mkString ""
}

sealed class JsonGetException(msg: String) extends RuntimeException(msg)

case class TypeMismatchException(foundType: JsonTypes.JsonType,
    expectedType: JsonTypes.JsonType, path: Vector[Either[Int, String]]) extends
    JsonGetException(s"Type mismatch: Expected ${expectedType.name} but found "+
    s"${foundType.name} at json${JsonGetException.stringifyPath(path)}")

case class MissingValueException(path: Vector[Either[Int, String]])
  extends JsonGetException(s"Missing value: json${JsonGetException.stringifyPath(path)}")

