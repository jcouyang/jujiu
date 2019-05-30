package us.oyanglul.jujiu
import scala.concurrent.ExecutionContext
import org.specs2.mutable.Specification
import cats.instances.list._
import cats.effect._
import scala.concurrent.ExecutionContext.Implicits.global
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
      Caffeine().sync
    ).unsafeRunSync() must_== ((None, "default", Some("now exist"), None))
  }

  "it should able to get and set async loading cache" >> {
    object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
      implicit val executionContext = global
    }
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val program = for {
      r1 <- cache.fetch(1)
      r2 <- cache.fetch(2)
      r3 <- cache.fetchAll(List[Integer](1, 2, 3))
    } yield (r1, r2, r3)

    val caffeineA = Caffeine()
      .executionContext(global)
      .expire(
        (_: Integer, _: String) => {
          1.second
        },
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration,
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration
      )
      .async((key: Integer) => IO("async string" + key))

    val caffeineB = Caffeine()
      .expireAfterAccess(1.second)
      .expireAfterWrite(2.seconds)
      .refreshAfterWrite(3.seconds)
      .async((key: Integer) => IO("async string" + key))

    val expected = (
      "async string1",
      "async string2",
      List("async string1", "async string2", "async string3")
    )
    program(caffeineA).unsafeRunSync() must_== expected
    program(caffeineB).unsafeRunSync() must_== expected
    program(Caffeine().async(_ => IO.raiseError(new Exception("something wrong")))).unsafeRunSync() must throwA[Exception]
  }
}
