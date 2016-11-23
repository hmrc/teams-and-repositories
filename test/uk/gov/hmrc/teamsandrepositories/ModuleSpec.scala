package uk.gov.hmrc.teamsandrepositories


import java.io.File

import com.google.inject.{Injector, Key, TypeLiteral}
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder


class ModuleSpec
  extends WordSpec
    with MockitoSugar
    with Matchers
{

  private val mockConfiguration = mock[Configuration]
  private val mockEnv = mock[play.api.Environment]

  "Play module loading from file" should {
    "give File when conf is on" in {

      val tempFile = File.createTempFile("test", "file")

      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(true))
      when(mockConfiguration.getString("cacheFilename")).thenReturn(Some("/some/tmp/file"))

      val application = new GuiceApplicationBuilder()
        .overrides(new Module(mockEnv, mockConfiguration))
        .disable(classOf[com.kenshoo.play.metrics.PlayModule])
        .configure(
          Map(
            "github.open.api.host" ->           "http://bla.bla",
            "github.open.api.user" ->           "",
            "github.open.api.key" ->            "",
            "github.enterprise.api.host" ->     "http://bla.bla",
            "github.enterprise.api.user" ->     "",
            "github.enterprise.api.key" ->      ""
          )
        )
        .build()

      val guiceInjector = application.injector.instanceOf(classOf[Injector])

      val key = Key.get(new TypeLiteral[DataGetter[TeamRepositories]]() {})

      guiceInjector.getInstance(key).isInstanceOf[FileDataGetter] shouldBe(true)

    }

  }


  "Play module loading from github" should {
    "produce MemCache Data Source when github integration is enabled via the configuration" in {

      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(Some(false))
      assertDataGetterIsGithubLoader

    }

    "produce MemCache Data Source when the relevant configuration flag is missing" in {

      when(mockConfiguration.getBoolean("github.offline.mode")).thenReturn(None)
      assertDataGetterIsGithubLoader
    }


  }

  def assertDataGetterIsGithubLoader: Unit = {
    when(mockConfiguration.getMilliseconds("cache.teams.duration")).thenReturn(Some(10000l))
    when(mockConfiguration.getString("github.open.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
    when(mockConfiguration.getString("github.open.api.user")).thenReturn(Some("Joe"))
    when(mockConfiguration.getString("github.open.api.key")).thenReturn(Some("Chicken"))

    when(mockConfiguration.getString("github.enterprise.api.host")).thenReturn(Some("http://yyz.g1thub.c0m"))
    when(mockConfiguration.getString("github.enterprise.api.user")).thenReturn(Some("something.enterprise.api3"))
    when(mockConfiguration.getString("github.enterprise.api.key")).thenReturn(Some("something.enterprise.api4"))
    when(mockConfiguration.getString("github.hidden.repositories")).thenReturn(None)
    when(mockConfiguration.getString("github.hidden.teams")).thenReturn(None)

    val application = new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .overrides(new Module(mockEnv, mockConfiguration))
      .build()


    val guiceInjector = application.injector.instanceOf(classOf[Injector])

    val key = Key.get(new TypeLiteral[DataGetter[TeamRepositories]]() {})

    guiceInjector.getInstance(key).isInstanceOf[GithubDataGetter] shouldBe (true)
  }


}
