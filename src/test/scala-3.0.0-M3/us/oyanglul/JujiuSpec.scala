package us.oyanglul.jujiu

import us.oyanglul.jujiu.syntax.caffeine._
import us.oyanglul.jujiu.syntax.cache._
import scala.concurrent.ExecutionContext
import org.specs2.mutable.Specification
import cats.effect._
import com.github.benmanes.caffeine.cache

class JujiuScala3Spec extends Specification with org.specs2.mock.Mockito:
  given ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  "works with IO" >> {
    "normal cache" >> {
      val c: Cache[IO, cache.Cache, String, String] = new CaffeineCache[IO, String, String]{}
      
      def program =
        for
          _ <- IO(println("something"))
          _ <- c.putF("key1", "value1")
          r1 <- c.fetchF("key1")
          r2 <- c.fetchF("key2", _ => IO("value2"))
          r3 <- c.fetchAllF(List("key1", "key2"))
          r4 <- c.parFetchAllF[List, IO.Par](List("key1", "key2"))
          _ <- c.clearF("key1")
        yield (r1, r2, r3, r4)

      given cache.Cache[String, String] = Caffeine().sync[String, String]
      program.unsafeRunSync() must_== (
        (
          Some("value1"),
          "value2",
          List(Some("value1"), Some("value2")),
          List(Some("value1"), Some("value2"))
        )
      )
    }

    "loading cache" >> {
      val c: LoadingCache[IO, cache.LoadingCache, String, String] =
        new CaffeineLoadingCache[IO, String, String] {}
      
      def program =
        for
          _ <- IO(println("something"))
          r1 <- c.fetchF("1")
          r2 <- c.fetchAllF(List("2", "3"))
          r3 <- c.parFetchAllF[List, IO.Par](List("4", "5"))
        yield (r1, r2, r3)

      given cache.LoadingCache[String, String] = Caffeine().sync(identity)
      program.unsafeRunSync() must_== (("1", List("2", "3"), List("4", "5")))
    }
  }

end JujiuScala3Spec
