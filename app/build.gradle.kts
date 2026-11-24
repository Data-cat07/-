plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("com.google.devtools.ksp")
 id("com.google.dagger.hilt.android")
 id("com.google.gms.google-services") version "4.4.2" apply false
}
if (file("google-services.json").isFile) apply(plugin = "com.google.gms.google-services")
android {
 namespace = "com.nolchin.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.nolchin.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.navigation:navigation-compose:2.8.5")
 implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
 implementation("com.google.dagger:hilt-android:2.52")
 ksp("com.google.dagger:hilt-compiler:2.52")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("io.coil-kt:coil-compose:2.7.0")
 implementation("com.google.code.gson:gson:2.11.0")
 implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-functions")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
 implementation("com.google.firebase:firebase-firestore")
 implementation("com.google.firebase:firebase-messaging")
}
