plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// GeckoView 内核版本：单一来源。依赖坐标与 BuildConfig 都从这里取值，
// 避免升级内核时漏改某一处、导致 UA 声称的版本与实际内核不一致。
val geckoViewVersion = "160.0.20261116130455"
// 仅保留主版本号（如 "160.0"），供 UA 生成使用
val geckoViewMajorVersion = geckoViewVersion.split('.').take(2).joinToString(".")

// Mirror酱 CDK：同样不写入源码。密钥一旦进仓库，任何人克隆即得，
// 官方文档也明确要求避免 CDK 泄漏。CI 用 Secrets 注入，本地可用 gradle.properties。
// 缺失不阻断构建：不带 CDK 仍能检查更新（只是少一项来源）。
val mirrorChyanCdk: String = (System.getenv("MIRRORCHYAN_CDK")
    ?: project.findProperty("mirrorchyan.cdk")?.toString()
    ?: "").trim()

// Mirror酱资源 ID：与 CDK 同级保密，同样由 secret 注入。
// 为空时 MirrorChyan.isAvailable 为 false，相关入口不展示且不发请求。
val mirrorChyanRid: String = (System.getenv("MIRRORCHYAN_RID")
    ?: project.findProperty("mirrorchyan.rid")?.toString()
    ?: "").trim()

// 赞助名单使用公开 HTTPS 地址；爱发电凭证只保留在服务端。
val sponsorFeedUrl: String = (System.getenv("SPONSOR_FEED_URL")
    ?: project.findProperty("sponsor.feedUrl")?.toString()
    ?: "").trim()

android {
    namespace = "com.zex.zexbrowse"
    // Compose 1.13（BOM 2026.11.01）的 AAR 元数据要求 compileSdk 37.1，
    // 带 minor 的 API 级别必须用块语法；sdkmanager 包名为 platforms;android-37.1
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zex.zexbrowse"
        minSdk = 29
        targetSdk = 37
        versionCode = 105
        versionName = "12.5.3"
        ndk { abiFilters += "arm64-v8a" }

        // GeckoView 内核主版本号（如 "160.0"），供 UA 生成与兜底使用
        buildConfigField("String", "GECKOVIEW_VERSION", "\"$geckoViewMajorVersion\"")

        // Mirror酱 CDK：只作为构建期注入的字符串，不入库、不打印。
        // 为空时应用退化为「仅用它检查版本号」，不影响从 GitHub 下载。
        val escapedCdk = mirrorChyanCdk.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "MIRRORCHYAN_CDK", "\"$escapedCdk\"")

        // Mirror酱资源 ID：与 CDK 同级保密，为空时相关功能整体不激活
        val escapedRid = mirrorChyanRid.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "MIRRORCHYAN_RID", "\"$escapedRid\"")

        val escapedSponsorFeedUrl = sponsorFeedUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n")
        buildConfigField("String", "SPONSOR_FEED_URL", "\"$escapedSponsorFeedUrl\"")
    }

    splits {
        abi { isEnable = false }
    }

    signingConfigs {
        val storeFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

configurations.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.2.10",
            "org.jetbrains.kotlin:kotlin-stdlib-common:2.2.10",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    // core 1.17.0：提供 NotificationCompat.ProgressStyle 与 setRequestPromotedOngoing，
    // 下载通知才能升级为 Android 16+ 的实时活动（Live Update）。
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // material3 固定 1.5.0：1.4.x 稳定版没有 ContainedLoadingIndicator（带容器背景的形变加载指示器），
    // 它在 1.5.0-beta02 才加入。该版本属于 beta，但 API 已定稿，且项目原本就已在用 1.5.0-beta01。
    implementation("androidx.compose.material3:material3:1.5.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    // GeckoView 内核：升级到 160（2026-11），此前为 158（2026-10）。
    // 浏览器内核直接渲染不可信网页，累积的沙箱逃逸、UAF、同源绕过等漏洞必须及时跟上。
    implementation("org.mozilla.geckoview:geckoview:$geckoViewVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
