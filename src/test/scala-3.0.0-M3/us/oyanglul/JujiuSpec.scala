package us.oyanglul.jujiu

import us.oyanglul.jujiu.syntax.caffeine._
import us.oyanglul.jujiu.syntax.cache._
import scala.concurrent.ExecutionContext
import org.specs2.mutable.Specification
import cats.effect._
import com.github.benmanes.caffeine.cache

class JujiuScala3Spec extends Specification:
  given ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  "works with IO" >> {
    "normal cache" >> {
      val dsl: Cache[IO, cache.Cache, String, String] = new CaffeineCache[IO, String, String]{}

      def program(using cache.Cache[String, String]) =
        for
          _ <- IO(println("something"))
          _ <- dsl.putF("key1", "value1")
          r1 <- dsl.fetchF("key1")
          r2 <- dsl.fetchF("key2", _ => IO("value2"))
          r3 <- dsl.fetchAllF(List("key1", "key2"))
          r4 <- dsl.parFetchAllF[List, IO.Par](List("key1", "key2"))
          _ <- dsl.clearF("key1")
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
  }
end JujiuScala3Spec
