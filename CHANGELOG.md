# Changelog

## 1.6.5

* First OSS release. Published to Maven Central [http://central.maven.org/maven2/com/pandora/hydra/], as Gradle Plugins repository does not allow side artifacts

## 1.7.0

* We added Android unit tests support, refactored code and split into gradle core, Java plugin and Android plugin. Added documentation for Android plugin

## 1.7.1

* Fixed bug with Android plugin not resetting JVM properties on balanced test tasks

## 1.7.2

* Added support for HTTP/HTTPS proxies
* Plugin can now be applied to Android projects with submodules

## 1.7.3

* Bump AGP version to 3.3.1
* Correctly setting variant names

## 1.7.4

* Added property to enable logging of client side exclusions
* Updated cache logic

## 1.7.5

* Fixed issue with test results missing when test is moved from one subproject to another (Issue 18)

## 2.0.0

* Switched to AGP v3.4