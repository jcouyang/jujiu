package us.oyanglul.jujiu
import com.github.benmanes.caffeine.cache.Caffeine
import org.specs2.mutable.Specification
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
}
