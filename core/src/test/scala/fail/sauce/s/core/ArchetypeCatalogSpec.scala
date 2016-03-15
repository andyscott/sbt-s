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

import scala.io.Source

import cats.Id
import cats.data.Xor

import org.scalatest._

class ArchetypeCatalogSpec extends FunSpec with Matchers {
  import ArchetypeCatalog.Entry

  @inline private[this] def resource(name: String): Source =
    Source.fromURL(getClass.getResource(s"/$name"))

  describe("XML loading") {

    it("ignores unrelated XML") {
      val catalogData = """
        <?xml version="1.0" encoding="UTF-8"?>
        <archetype-catalog
          xmlns="http://maven.apache.org/plugins/maven-archetype-plugin/archetype-catalog/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/plugins/maven-archetype-plugin/archetype-catalog/1.0.0 http://maven.apache.org/xsd/archetype-catalog-1.0.0.xsd">
          <bojangles>
            <doesnt-matter>ignore</doesnt-matter>
          </bojangles>
        </archetype-catalog>""".trim

      val catalog = ArchetypeCatalog.load(catalogData)

      catalog should equal(Xor.right(ArchetypeCatalog(entries = Nil)))
    }

    it("handles a simple catalog with one entry") {
      val catalogData = """
        <?xml version="1.0" encoding="UTF-8"?>
        <archetype-catalog
          xmlns="http://maven.apache.org/plugins/maven-archetype-plugin/archetype-catalog/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/plugins/maven-archetype-plugin/archetype-catalog/1.0.0 http://maven.apache.org/xsd/archetype-catalog-1.0.0.xsd">
          <archetypes>
            <archetype>
              <groupId>org.appfuse.archetypes</groupId>
              <artifactId>appfuse-basic-jsf</artifactId>
              <version>2.0</version>
              <repository>http://static.appfuse.org/releases</repository>
              <description>AppFuse archetype for creating a web application with Hibernate, Spring and JSF</description>
            </archetype>
          </archetypes>
        </archetype-catalog>""".trim

      val catalog = ArchetypeCatalog.load(catalogData)

      catalog should equal(Xor.right(ArchetypeCatalog(entries = List(
        Entry[Id, Id, Id, Option, Option](
          "org.appfuse.archetypes", "appfuse-basic-jsf", "2.0",
          Some("http://static.appfuse.org/releases"),
          Some("AppFuse archetype for creating a web application with Hibernate, Spring and JSF")
        )
      ))))
    }

    it("handles large catalogs") {
      val res = ArchetypeCatalog.load(
        resource("central_archetype-catalog.xml")
      )
      assert(res.isRight)
    }
  }

}
