#include <jni.h>
#include "receive.hpp"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_lumitalk_NativeBridge_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray frameData,
        jint width,
        jint height) {

    jbyte* data = env->GetByteArrayElements(frameData, nullptr);
    std::string result = processFrame(
        reinterpret_cast<const unsigned char*>(data),
        width,
        height
    );
    env->ReleaseByteArrayElements(frameData, data, JNI_ABORT);

    return env->NewStringUTF(result.c_str());
}
