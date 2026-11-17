pluginManagement {
    repositories {
        // dl.google.com 在本机会被间歇性掐断 TLS，优先走国内镜像
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
    }
}

rootProject.name = "xiaonian"
include(":app")
