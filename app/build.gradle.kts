plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.gms.google.services)
  alias(libs.plugins.ktfmt.format)
}

android {
  // Do not change this value, as it must reflect the directory structure of the code.
  // This is the package into which the "R" class is generated.
  // Change "applicationId" below, if needed.
  namespace = "app.android.issue5101"
  compileSdk = 34

  defaultConfig {
    // Optional: Change "applicationId" below to match a "package_name" in your google-services.json
    applicationId = "app.android.issue5101"
    minSdk = 30
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  buildFeatures {
    viewBinding = true
  }
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.constraintlayout)

  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.appcheck)
}
