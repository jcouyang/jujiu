package us.oyanglul.jujiu

import us.oyanglul.jujiu.syntax.caffeine._
import us.oyanglul.jujiu.syntax.cache._
import cats.{Applicative}
import cats.data.Kleisli
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext
import org.specs2.mutable.Specification
import cats.instances.list._
import cats.syntax.all._
import cats.effect._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.github.benmanes.caffeine.cache
import us.oyanglul.jujiu.syntax.CaffeineSyntax

class JujiuSpec extends Specification with org.specs2.mock.Mockito {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "it should able to get and set cache" >> {
    object cacheDsl extends CaffeineCache[IO, String, String] // <-
    val program = for {
      r1 <- cacheDsl.fetch("not exist yet") // <-
      r2 <- cacheDsl.fetch("not exist yet", _ => IO("default")) // <-
      _ <- cacheDsl.put("not exist yet", "now exist") // <-
      r3 <- cacheDsl.fetch("not exist yet")
      _ <- cacheDsl.clear("not exist yet")
      r4 <- cacheDsl.fetch("not exist yet")
    } yield (r1, r2, r3, r4)
    program(Caffeine().sync) // <-
      .unsafeRunSync() must_== ((None, "default", Some("now exist"), None))
  }

  "it should IO error when async load failure" >> {
    object dsl extends CaffeineAsyncCache[IO, String, String] {
      implicit val executionContext = global
    }
    val program = for {
      r1 <- dsl.fetch("not exist yet")
      r2 <- dsl.fetch("not exist yet", _ => IO("default"))
    } yield (r1, r2)

    val failCache = mock[cache.AsyncCache[String, String]]
    failCache
      .getIfPresent("not exist yet")
      .returns(
        CompletableFuture.supplyAsync(() => IO.raiseError[String](new Exception("cache load error")).unsafeRunSync())
      )

    program(
      failCache
    ).unsafeRunSync() must throwA[Exception](message = "cache load error")
  }

  "works with IO" >> {
    "normal cache" >> {
      val c: Cache[IO, cache.Cache, String, String] = new CaffeineCache[IO, String, String] {}
      implicit val cacheProvider: cache.Cache[String, String] = Caffeine().sync[String, String]
      def program =
        for {
          _ <- IO(println("something"))
          _ <- c.putF("key1", "value1")
          r1 <- c.fetchF("key1")
          r2 <- c.fetchF("key2", _ => IO("value2"))
          r3 <- c.fetchAllF(List("key1", "key2"))
          r4 <- c.parFetchAllF[List, IO.Par](List("key1", "key2"))
          _ <- c.clearF("key1")
        } yield (r1, r2, r3, r4)
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
      val c: LoadingCache[IO, cache.LoadingCache, String, String] = new CaffeineLoadingCache[IO, String, String] {}
      implicit val cacheProvider: cache.LoadingCache[String, String] = Caffeine().sync(identity)
      def program =
        for {
          _ <- IO(println("something"))
          r1 <- c.fetchF("1")
          r2 <- c.fetchAllF(List("2", "3"))
          r3 <- c.parFetchAllF[List, IO.Par](List("4", "5"))
        } yield (r1, r2, r3)
      program.unsafeRunSync() must_== (("1", List("2", "3"), List("4", "5")))
    }
  }

  "it should able to get and set async loading cache" >> {
    object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
      implicit val executionContext = global // <-- (ref:executionContext)
    }

    val program = for {
      r1 <- cache.fetch(1)
      r2 <- cache.fetch(2)
      r3 <- cache.fetchAll(List[Integer](1, 2, 3))
    } yield (r1, r2, r3)

    val caffeineA: com.github.benmanes.caffeine.cache.AsyncLoadingCache[Integer, String] = Caffeine()
      .executionContext(global) // <-- (ref:global)
      .withExpire( // <-- (ref:expire)
        (_: Integer, _: String) => {
          1.second
        },
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration,
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration
      )
      .async((key: Integer) => IO("async string" + key)) // <-- (ref:async)

    val caffeineB = Caffeine()
      .withExpireAfterAccess(1.second)
      .withExpireAfterWrite(2.seconds)
      .withRefreshAfterWrite(3.seconds)
      .async((key: Integer) => IO("async string" + key))

    val expected = (
      "async string1",
      "async string2",
      List("async string1", "async string2", "async string3")
    )
    program(caffeineA).unsafeRunSync() must_== expected
    program(caffeineB).unsafeRunSync() must_== expected
    program(Caffeine().async(_ => IO.raiseError(new Exception("something wrong"))))
      .unsafeRunSync() must throwA[Exception]
  }

  "works with tagless final" >> {
    trait LogDsl[F[_]] {
      def log(msg: String): F[Unit]
    }

    type ProgramDsl[F[_]] = CaffeineCache[F, String, String] with LogDsl[F]

    def program[F[_]: Async](dsl: ProgramDsl[F])(implicit ev: cache.Cache[String, String]): F[Option[String]] =
      for {
        value <- dsl.fetchF("key")
        _ <- dsl.log("something")
      } yield value

    {
      object dsl extends CaffeineCache[IO, String, String] with LogDsl[IO] {
        def log(msg: String) = IO(println(msg))
      }

      implicit val cacheProvider: cache.Cache[String, String] = Caffeine().sync[String, String]

      program[IO](dsl).unsafeRunSync() must_== None
    }
  }

  "works with tagless final style readerT" >> {
    // Layer 1: Environment
    trait HasLogger {
      def logger: String => Unit
    }
    trait HasCacheProvider {
      def cacheProvider: cache.Cache[String, String]
    }

    type Env = HasLogger with HasCacheProvider

    // Layer 2: DSL
    trait LogDsl[F[_]] {
      def log(msg: String)(implicit M: Applicative[F]): Kleisli[F, Env, Unit] = Kleisli(a => M.pure(a.logger(msg)))
    }

    type Dsl[F[_]] = CaffeineCache[F, String, String] with LogDsl[F]

    // Layer 3: Business
    def program[F[_]](dsl: Dsl[F])(
      implicit ev: Async[F]
    ) =
      for {
        _ <- dsl.log("something")
        value <- dsl.fetch("key").local[Env](_.cacheProvider)
      } yield value

    object dsl extends CaffeineCache[IO, String, String] with LogDsl[IO]

    program[IO](dsl)
      .run(new HasLogger with HasCacheProvider {
        def logger = println
        def cacheProvider = Caffeine().sync
      })
      .unsafeRunSync() must_== None
  }

  "run on redis" >> {
    import redis.clients.jedis._

    def program[F[_]: Async, S[_, _]](dsl: Cache[F, S, String, String]) =
      for {
        r1 <- dsl.fetch("not exist yet")
        r2 <- dsl.fetch("not exist yet", _ => Async[F].delay("default"))
        _ <- dsl.put("not exist yet", "now exist")
        r3 <- dsl.fetch("not exist yet")
        _ <- dsl.clear("not exist yet")
        r4 <- dsl.fetch("not exist yet")
      } yield (r1, r2, r3, r4)

    type J[A, B] = Jedis
    object dsl extends Cache[IO, J, String, String] {
      def put(k: String, v: String)(implicit M: Async[IO]): Kleisli[IO, Jedis, Unit] =
        Kleisli { redis =>
          M.delay {
            redis.set(k, v)
            ()
          }
        }
      def fetch(k: String)(implicit M: Async[IO]): Kleisli[IO, Jedis, Option[String]] =
        Kleisli(redis => M.delay(Option(redis.get(k))))
      def clear(k: String)(implicit M: Async[IO]): Kleisli[IO, Jedis, Unit] =
        Kleisli(
          redis =>
            M.delay {
              redis.del(k)
              ()
            }
        )
    }

    program(dsl)
      .run(
        new Jedis("localhost")
      )
      .unsafeRunSync() must_== ((None, "default", Some("now exist"), None))
  }.pendingUntilFixed("Redis")
}
