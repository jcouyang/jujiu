package us.oyanglul.jujiu
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.Executor

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executor

import org.specs2.mutable.Specification
import cats.instances.list._
import cats.effect._

import scala.concurrent.duration._
import syntax.caffeine._

class JujiuSpec extends Specification {
  "it should able to get and set cache" >> {
    object cache extends CaffeineCache[IO, String, String]
    val program = for {
      r1 <- cache.fetch("not exist yet")
      r2 <- cache.fetch("not exist yet", _ => IO("default"))
      _ <- cache.put("not exist yet", "now exist")
      r3 <- cache.fetch("not exist yet")
      _ <- cache.clear("not exist yet")
      r4 <- cache.fetch("not exist yet")
    } yield (r1, r2, r3, r4)
    program(
      Caffeine
        .newBuilder()
        .build()
    ).unsafeRunSync() must_== ((None, "default", Some("now exist"), None))
  }

  "it should able to get and set async loading cache" >> {
    import scala.concurrent.ExecutionContext.Implicits.global
    object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
      implicit val executionContext = global
    }
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val program = for {
      r1 <- cache.fetch(1)
      r2 <- cache.fetch(2)
      r3 <- cache.fetchAll(List[Integer](1,2,3))
    } yield (r1, r2, r3)

    val caffeine = Caffeine
      .newBuilder()
      .executor(new Executor {
        def execute(r: Runnable) =
          global.execute(r)
      })
      .expireAfterAccess(1 second)
      .async((key: Integer) => IO("async string" + key))

    program(caffeine).unsafeRunSync() must_== (
      (
        "async string1",
        "async string2",
        List("async string1", "async string2", "async string3")
      )
    )
  }
}
