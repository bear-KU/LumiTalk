#include <jni.h>
#include "receive.hpp"
#include "receive_center.hpp"
#include <mutex>

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

static std::once_flag center_init_flag;

extern "C" JNIEXPORT void JNICALL
Java_com_lumitalk_NativeBridge_pushFrameCenter(
    JNIEnv* env, jobject,
    jbyteArray frameData, jint width, jint height)
{
    initReceiveCenter();
    jbyte* data = env->GetByteArrayElements(frameData, nullptr);
    pushFrameCenter(reinterpret_cast<const uint8_t*>(data), width, height);
    env->ReleaseByteArrayElements(frameData, data, JNI_ABORT);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumitalk_NativeBridge_getResultCenter(JNIEnv* env, jobject)
{
    return env->NewStringUTF(getResultCenter().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_lumitalk_NativeBridge_stopReceiveCenter(JNIEnv* env, jobject)
{
    stopReceiveCenter();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lumitalk_NativeBridge_getStateCenter(JNIEnv* env, jobject)
{
    return static_cast<jboolean>(getStateCenter());
}
