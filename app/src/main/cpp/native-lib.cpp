#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jint JNICALL
Java_com_lumitalk_NativeBridge_add(
        JNIEnv* env,
        jobject /* this */,
        jint a,
        jint b) {
    return a + b;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_lumitalk_NativeBridge_helloFromCpp(
        JNIEnv* env,
        jobject /* this */) {

    std::string message = "Hello from C++!";
    return env->NewStringUTF(message.c_str());
}
