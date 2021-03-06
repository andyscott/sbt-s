/*
 * s-core copyright 2016 Andy Scott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fail.sauce.s
package core

import scala.language.higherKinds

import scala.annotation.tailrec
import scala.io.Source
import scala.xml.pull._

import cats._
import cats.data.Xor
import cats.std.list._
import cats.syntax.semigroup._
import cats.syntax.traverse._

/** A Maven archetype catalog.
  */
case class ArchetypeCatalog(
  entries: List[ArchetypeCatalog.LoadedEntry]
)

object ArchetypeCatalog {

  /** An entry in an archetype catalog. The various types are abstracted
    * so overall type can represent partial as well as complete entries.
    */
  case class Entry[G[_], A[_], V[_], R[_], D[_]](
    groupId:     G[String],
    artifactId:  A[String],
    version:     V[String],
    repository:  R[String],
    description: D[String]
  )

  type LoadedEntry = Entry[Id, Id, Id, Option, Option]

  /** Load a catalog from an XML string */
  def load(data: String): Xor[String, ArchetypeCatalog] =
    load(Source.fromString(data))

  /** Load a catalog from an XML source */
  def load(source: Source): Xor[String, ArchetypeCatalog] = {
    // format: OFF

    val xml = new XMLEventReader(source)

    type Acc = List[String Xor LoadedEntry]
    val (_, entries) =
      loopUntil[Acc]("archetype-catalog", xml.toList, Nil) { r0 ⇒
      loopUntil[Acc]("archetypes"       , r0        , Nil) { r1 ⇒
      loopUntil[Acc]("archetype"        , r1        , Nil) { r2 ⇒
        val (r3, entry) = readArchetype(r2, EmptyPartialEntry)
        r3 → List(entry)
      }}}

    entries.sequenceU.map(entries0 ⇒
      ArchetypeCatalog(entries = entries0))

    // format: ON
  }

  private type PartialEntry = Entry[Option, Option, Option, Option, Option]

  @inline private[this] def EmptyPartialEntry =
    Entry[Option, Option, Option, Option, Option](
      None, None, None, None, None
    )

  private type Events = List[XMLEvent]

  @tailrec private[this] def readArchetype(
    events: Events,
    entry:  PartialEntry
  ): (Events, String Xor LoadedEntry) = events match {
    case head :: tail ⇒ head match {
      case EvElemStart(_, label, _, _) ⇒
        val (tail0, text) = readText(tail, label)
        text match {
          case Xor.Right(text0) ⇒ label match {
            case "groupId" ⇒
              if (entry.groupId.isDefined)
                tail0 → Xor.left("groupId is already set")
              else
                readArchetype(tail0, entry.copy(groupId = Some(text0)))
            case "artifactId" ⇒
              if (entry.artifactId.isDefined)
                tail0 → Xor.left("artifactId is already set")
              else
                readArchetype(tail0, entry.copy(artifactId = Some(text0)))
            case "version" ⇒
              if (entry.version.isDefined)
                tail0 → Xor.left("version is already set")
              else
                readArchetype(tail0, entry.copy(version = Some(text0)))
            case "repository" ⇒
              if (entry.repository.isDefined)
                tail0 → Xor.left("repository is already set")
              else
                readArchetype(tail0, entry.copy(repository = Some(text0)))
            case "description" ⇒
              if (entry.description.isDefined)
                tail0 → Xor.left("description is already set")
              else
                readArchetype(tail0, entry.copy(description = Some(text0)))
            case _ ⇒
              exitScope(tail0, 0) → Xor.left(
                s"unrecognized label $label with value $text"
              )
          }

          case Xor.Left(ohCrap) ⇒ exitScope(tail0, 0) → Xor.left(ohCrap)
        }

      case EvText(text) if text.trim.length == 0 ⇒
        readArchetype(tail, entry)

      case EvElemEnd(_, "archetype") ⇒
        entry match {
          case Entry(Some(groupId), Some(artifactId), Some(version), repository, description) ⇒
            tail → Xor.right(Entry[Id, Id, Id, Option, Option](groupId, artifactId, version, repository, description))
          case _ ⇒ tail → Xor.left(s"archetype $entry prematurely closed")
        }

      case _ ⇒
        exitScope(events, 0) → Xor.left(
          s"unexpected XML sequence when reading archetype $entry"
        )
    }

    case Nil ⇒ Nil → Xor.left(
      "unexpected end of stream when reading archetype"
    )
  }

  // loops through the event stream until we exit the current scope
  @tailrec private[this] def exitScope(
    events: Events, depth: Int
  ): Events = events match {
    case head :: tail ⇒ head match {
      case EvElemStart(_, _, _, _) ⇒ exitScope(tail, depth + 1)
      case EvElemEnd(_, _) ⇒
        if (depth == 0) tail
        else exitScope(tail, depth - 1)
      case _ ⇒ exitScope(tail, depth)
    }

    case Nil ⇒ Nil
  }

  // loops through the event stream until a specific label is started,
  // then delegates to function f
  private[this] def loopUntil[T: Semigroup](
    stopLabel: String, events: Events, acc: T
  )(f: Events ⇒ (Events, T)): (Events, T) = {

    @tailrec def loop(
      events: Events, acc: T
    ): (Events, T) = events match {
      case head :: tail ⇒ head match {
        case EvElemStart(_, label, _, _) ⇒
          if (label == stopLabel) {
            val (events0, acc0) = f(tail)
            loop(events0, acc0 |+| acc)
          } else {
            loop(exitScope(tail, 0), acc)
          }
        case EvElemEnd(_, label) ⇒ tail → acc
        case _                   ⇒ loop(tail, acc)
      }

      case Nil ⇒ Nil → acc
    }

    loop(events, acc)
  }

  // reads text until a closing XML tag of a given label
  private[this] def readText(
    events: Events, stopLabel: String
  ): (Events, String Xor String) = {

    @tailrec def loop(
      events: Events, text: String
    ): (Events, String Xor String) = events match {
      case head :: tail ⇒ head match {
        case EvText(moreText)          ⇒ loop(tail, text + moreText)
        case EvEntityRef(moreText)     ⇒ loop(tail, text + moreText)
        case EvElemEnd(_, `stopLabel`) ⇒ tail → Xor.right(text)
        case _ ⇒
          events → Xor.left(
            s"unexpected XML element $head when reading text " +
              s"for $stopLabel"
          )
      }
      case Nil ⇒
        Nil → Xor.left(
          s"unexpected end of stream when reading text for $stopLabel"
        )
    }

    loop(events, "")
  }

}
