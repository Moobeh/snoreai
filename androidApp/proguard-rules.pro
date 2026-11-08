# LiteRT (TensorFlow Lite) uses JNI and reflection.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
