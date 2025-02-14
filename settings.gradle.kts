plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "ai-devs"

includeBuild("simple-conventions")
include("simple")
include(":simple:common")
include(":simple:taskpoligon")
include(":simple:task0101")
include(":simple:task0102")
include(":simple:task0103")
include(":simple:task0104")
include(":simple:task0105")
include(":simple:task0201")
include(":simple:task0202")
include(":simple:task0203")
include(":simple:task0204")
include(":simple:task0205")
include(":simple:task0301")
include(":simple:task0302")
include(":simple:task0303")
include(":simple:task0304")
include(":simple:task0305")
include(":simple:task0401")
include(":simple:task0402")
include(":simple:task0403")
include(":simple:task0405")

includeBuild("task0404")
