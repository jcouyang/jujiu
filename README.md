# <ruby><rb>雎鳩</rb><rt>ju jiu</rt></ruby>

Functional Scala Caching

[![CircleCI](https://circleci.com/gh/jcouyang/jujiu.svg?style=svg)](https://circleci.com/gh/jcouyang/jujiu) \
![Cats Friendly Badge](https://typelevel.org/cats/img/cats-badge-tiny.png) 

[<img src=https://upload.wikimedia.org/wikipedia/commons/7/7e/Imperial_Encyclopaedia_-_Animal_Kingdom_-_pic009_-_%E9%9B%8E%E9%B3%A9%E5%9C%96.svg width=50%/>](https://en.wikisource.org/wiki/zh:%E5%8F%A4%E4%BB%8A%E5%9C%96%E6%9B%B8%E9%9B%86%E6%88%90/%E5%8D%9A%E7%89%A9%E5%BD%99%E7%B7%A8/%E7%A6%BD%E8%9F%B2%E5%85%B8/%E7%AC%AC011%E5%8D%B7)
> 关关雎鸠 在河之洲

# make [Caffeine](https://github.com/ben-manes/caffeine) idiomatic to Cats

```scala
    object cache extends CaffeineCache[IO, String, String]
    val program = for {
      r1 <- cache.fetch("not exist yet")
      r2 <- cache.fetch("not exist yet", _ => IO("default"))
      _ <- cache.put("not exist yet", "overrided")
      r3 <- cache.fetch("not exist yet")
    } yield (r1, r2, r3)
    program(Caffeine().sync) must_== ((None, "default", "overrided"))
```

## and Cats IO

```scala
    import scala.concurrent.ExecutionContext.Implicits.global
    object cache extends CaffeineAsyncLoadingCache[IO, Integer, String] {
      implicit val executionContext = global  // provide excution context for exec Future
    }
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global) // context shift for parallel fetchAll
    
    val program = for {
      r1 <- cache.fetch(1)
      r2 <- cache.fetch(2)
      r3 <- cache.fetchAll(List[Integer](1, 2, 3))
    } yield (r1, r2, r3)

    val caffeine = Caffeine()
      .executionContext(global) //
      .expire(
        (_: Integer, _: String) => { 1.second }, // after create
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration, // after write
        (_: Integer, _: String, currentDuration: FiniteDuration) => currentDuration // after read
      )
      .async((key: Integer) => IO("async string" + key))

    program(caffeine).unsafeRunSync() must_== (
      (
        "async string1",
        "async string2",
        List("async string1", "async string2", "async string3")
      )
    )
```
