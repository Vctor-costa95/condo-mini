import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.condo_mini"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.appcondomini"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Endereço da API. Vazio = dados de demonstração em memória (repositórios Fake).
        // Para a API local no emulador: `adb reverse tcp:8080 tcp:8080` e
        // -Pcondomini.apiUrl=http://127.0.0.1:8080. O 10.0.2.2 não serve: no Android 17,
        // endereço de rede local exige a permissão ACCESS_LOCAL_NETWORK, e o app não pede.
        val apiUrl = providers.gradleProperty("condomini.apiUrl")
            .orElse("https://api-condomini.tcpneus-logistica.com/api-condomini").get()
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        manifestPlaceholders["cleartextTraffic"] = apiUrl.startsWith("http://").toString()

        // Firebase (notificações push), lido do app/google-services.json baixado do console do
        // Firebase, sem o plugin do Google. Sem o arquivo, o app funciona sem notificações.
        val firebase = firebaseConfig(file("google-services.json"), applicationId!!)
        listOf("APP_ID", "API_KEY", "PROJECT_ID", "SENDER_ID").forEach { key ->
            buildConfigField("String", "FIREBASE_$key", "\"${firebase[key].orEmpty()}\"")
        }
    }

    // Chave de release fora do git: keystore.properties na raiz aponta para o .jks.
    // Sem o arquivo, o release sai sem assinatura (não instala).
    val signingProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingProps?.let { signingConfig = signingConfigs.getByName("release") }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time em APIs < 26 (minSdk 24)
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/** Os campos do google-services.json que o FirebaseOptions precisa, para o pacote [packageName]. */
fun firebaseConfig(file: File, packageName: String): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val json = groovy.json.JsonSlurper().parse(file) as Map<*, *>
    val project = json["project_info"] as Map<*, *>
    val client = (json["client"] as List<*>).map { it as Map<*, *> }.firstOrNull { client ->
        val info = client["client_info"] as Map<*, *>
        (info["android_client_info"] as Map<*, *>)["package_name"] == packageName
    } ?: error("google-services.json não tem o app $packageName")
    return mapOf(
        "APP_ID" to (client["client_info"] as Map<*, *>)["mobilesdk_app_id"].toString(),
        "API_KEY" to ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"].toString(),
        "PROJECT_ID" to project["project_id"].toString(),
        "SENDER_ID" to project["project_number"].toString()
    )
}
