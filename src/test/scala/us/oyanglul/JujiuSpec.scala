package us.oyanglul.jujiu
import com.github.benmanes.caffeine.cache.{ AsyncCacheLoader,
  AsyncLoadingCache => ALC,
  Caffeine }
import scala.concurrent.ExecutionContext
import java.util.concurrent.{ CompletableFuture, Executor }
import org.specs2.mutable.Specification
import cats.instances.list._
import cats.effect._

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
      .newBuilder().build()
    ).unsafeRunSync() must_== ((None, "default", Some("now exist"), None))
  }

  "it should able to get and set async loading cache" >> {
    import scala.concurrent.ExecutionContext.Implicits.global
     object cache extends CaffeineAsyncLoadingCache[IO, String, String] {
       implicit val executionContext = global
     }
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val program = for {
      r1 <- cache.fetch("1")
      r2 <- cache.fetch("2")
      r3 <- cache.fetchAll[List, IO.Par](List("1","2","3"))
    } yield (r1, r2, r3)

    val caffeine: ALC[String, String] = Caffeine.newBuilder()
      .executor(new Executor {
        def execute(r: Runnable) = {
          global.execute(r)
        }
      })
       .buildAsync(new AsyncCacheLoader[String, String] {
        def asyncLoad(key: String, executor: Executor) = {
          CompletableFuture.supplyAsync(
            () => "async string" + key,
            executor
          )
        }
      })
    program(caffeine).unsafeRunSync() must_== (("async string1", "async string2", List("async string1", "async string2", "async string3")))
  }
}
