package us.oyanglul.jujiu

package object syntax {
  object caffeine extends CaffeineSyntax
  object cache extends CacheSyntax with LoadingCacheSyntax
}
