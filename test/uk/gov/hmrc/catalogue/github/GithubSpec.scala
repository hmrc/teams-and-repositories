/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.catalogue.github

import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar

import scala.concurrent.Future

class GithubSpec extends WordSpec with MockitoSugar with Matchers{

  val githubHttp: GithubHttp = mock[GithubHttp]

  val gitHub = new Github(githubHttp)


  "getTeamRepoMapping" should {
    "return mapping of team to repositories they own" in {


      when(githubHttp.host).thenReturn("myHost")


      when(githubHttp.get[List[String]]("https://myHost/api/v3/user/orgs")).thenReturn(Future.successful(List.empty[String]))



    }
  }




}
