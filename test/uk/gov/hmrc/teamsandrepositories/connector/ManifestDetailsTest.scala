/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories.connector

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.connector.GhRepository.ManifestDetails
import uk.gov.hmrc.teamsandrepositories.model.{RepoType, Organisation, ServiceType, Tag, TestType}

import java.time.Instant

class ManifestDetailsTest extends AnyWordSpec with Matchers:

  "ManifestDetails.Parse" should:

    "handle empty yaml string" in:
      val details = ManifestDetails.parse("repo1", "")
      details.isDefined        shouldBe false

    "default organisation to Mdtp when given a yaml string" in:
      val details = ManifestDetails.parse("repo1", "some-key: some-value")
      details.isDefined        shouldBe true
      details.get.organisation shouldBe Some(Organisation.Mdtp)

    "parse mdtp organisation" in:
      val details = ManifestDetails.parse("repo1", "organisation: mdtp")
      details.isDefined        shouldBe true
      details.get.organisation shouldBe Some(Organisation.Mdtp)

    "parse external organisation" in:
      val details = ManifestDetails.parse("repo1", "organisation: some-organisation")
      details.isDefined        shouldBe true
      details.get.organisation shouldBe Some(Organisation.External("some-organisation"))

    "parse description" in:
      val manifest =
        """
          |description: test description
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.description shouldBe Some("test description")

    "parse end-of-life-date" in:
      val manifest =
        """
          |end-of-life-date: 2024-05-09
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe Some(Instant.parse("2024-05-09T00:00:00Z"))

    "parse end-of-life-date as None if invalid format" in:
      val manifest =
        """
          |end-of-life-date: 20000-05-09
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe None

    "parse end-of-life-date as None if key is missing" in:
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined shouldBe true
      details.get.endOfLifeDate shouldBe None

    "parse default to not deprecated" in:
      val manifest =
        """
          |repoVisibility: public_0C3F0CE3E6E6448FAD341E7BFA50FCD333E06A20CFF05FCACE61154DDBBADF71
          |type: library
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined        shouldBe true
      details.get.repoType     shouldBe Some(RepoType.Library)
      details.get.isDeprecated shouldBe false

    "parse service-type" in:
      val manifest =
        """
          |type: service
          |service-type: frontend
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)

    "parse invalid service-type" in:
      val manifest =
        """
          |type: service
          |service-type: bad
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe None

    "parse tags" in:
      val manifest =
        """
          |type: service
          |service-type: frontend
          |tags: ['api', 'stub', 'admin']
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)
      details.get.tags        shouldBe Some(Set(Tag.Api, Tag.Stub, Tag.AdminFrontend))

    "parse invalid tag" in:
      val manifest =
        """
          |type: service
          |service-type: frontend
          |tags: ['bad']
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.isDefined       shouldBe true
      details.get.repoType    shouldBe Some(RepoType.Service)
      details.get.serviceType shouldBe Some(ServiceType.Frontend)
      details.get.tags        shouldBe Some(Set.empty)

    "parse prototype-auto-publish" in:
      val manifest =
        """
          |prototype-name: my-prototype
          |prototype-auto-publish: true
          |""".stripMargin

      val details = ManifestDetails.parse("my-prototype", manifest)
      details.isDefined                shouldBe true
      details.get.prototypeName        shouldBe Some("my-prototype")
      details.get.prototypeAutoPublish shouldBe Some(true)

    "parse digital-service" in:
      val manifest =
        """
          |type: service
          |service-type: frontend
          |digital-service: MTD
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.fold(fail("Unable to parse yaml")): data =>
        data.digitalServiceName.fold(fail("Unable to parse digital-service in yaml")): dsn =>
          dsn shouldBe "MTD"

    "parse and format digital-service" in:
      val input    = "a service-name with a hyphen and a trailing Space "
      val expected = "A Service-Name With A Hyphen And A Trailing Space"
      val manifest =
        s"""
          |type: service
          |service-type: frontend
          |digital-service: $input
          |""".stripMargin

      val details = ManifestDetails.parse("repo1", manifest)
      details.fold(fail("Unable to parse yaml")): data =>
        data.digitalServiceName.fold(fail("Unable to parse digital-service in yaml")): dsn =>
          dsn shouldBe expected

    "parse acceptance test type when defined" in:
      val manifest =
        """
          |type: test
          |test-type: acceptance
          |""".stripMargin

      val details = ManifestDetails.parse("test-repo", manifest)
      details.fold(fail("Unable to parse yaml")): data =>
        data.testType.fold(fail("Unable to parse test-type in yaml")): tt =>
          tt shouldBe TestType.Acceptance

    "parse performance test type when defined" in:
      val manifest =
        """
          |type: test
          |test-type: performance
          |""".stripMargin

      val details = ManifestDetails.parse("test-repo", manifest)
      details.fold(fail("Unable to parse yaml")): data =>
        data.testType.fold(fail("Unable to parse test-type in yaml")): tt =>
          tt shouldBe TestType.Performance

    "derive test type from repo name" in:
      ManifestDetails.deriveTestType("example-performance-tests")
        .fold(fail("Unable to parse test-type from performance-tests in repo name")): tt =>
          tt shouldBe TestType.Performance

      ManifestDetails.deriveTestType("example-perf-tests")
        .fold(fail("Unable to parse test-type from perf-tests in repo name")): tt =>
          tt shouldBe TestType.Performance

      ManifestDetails.deriveTestType("example-acceptance-tests")
        .fold(fail("Unable to parse test-type from acceptance-tests in repo name")): tt =>
          tt shouldBe TestType.Acceptance

      ManifestDetails.deriveTestType("example-ui-tests")
        .fold(fail("Unable to parse test-type from ui-tests in repo name")): tt =>
          tt shouldBe TestType.Acceptance

      ManifestDetails.deriveTestType("example-journey-tests")
        .fold(fail("Unable to parse test-type from journey-tests in repo name")): tt =>
          tt shouldBe TestType.Acceptance

      ManifestDetails.deriveTestType("example-api-tests")
        .fold(fail("Unable to parse test-type from api-tests in repo name")): tt =>
          tt shouldBe TestType.Acceptance

      ManifestDetails.deriveTestType("example-contract-tests")
        .fold(fail("Unable to parse test-type from contract-tests in repo name")): tt =>
          tt shouldBe TestType.Contract
