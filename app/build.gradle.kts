plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

kapt {
    javacOptions {
        // Work around JDK 21 module encapsulation if Gradle JDK is 21+
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
        option("-J--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED")
        option("-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    }
}

android {
    namespace = "com.example.kolki"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kolki"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Vosk speech recognition (commented out for now)
    // implementation(libs.vosk.android)
    
    // existing deps
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}