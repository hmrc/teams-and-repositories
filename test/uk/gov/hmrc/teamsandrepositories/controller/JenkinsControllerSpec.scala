package uk.gov.hmrc.teamsandrepositories.controller

import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.Future

class JenkinsControllerSpec extends PlaySpec with Results with MockitoSugar {

  val mockJenkinsService = mock[JenkinsService]

  "JenkinsController" should {
    "return a single match as Json" in {
      when(mockJenkinsService.findByService("foo")) thenReturn Future.successful(Some(BuildJob("foo", "http://bar/job/api/")))

      val controller = new JenkinsController(mockJenkinsService, stubControllerComponents())
      val result = controller.lookup("foo").apply(FakeRequest())
      val bodyText = contentAsString(result)
      bodyText mustBe """{"service":"foo","jenkinsURL":"http://bar/job/api/"}"""
    }

    "return a no content when no matches found" in {
      when(mockJenkinsService.findByService("bar")) thenReturn Future.successful(None)

      val controller = new JenkinsController(mockJenkinsService, stubControllerComponents())
      val result = controller.lookup("bar").apply(FakeRequest())
      status(result) mustBe 204
    }
  }
}
