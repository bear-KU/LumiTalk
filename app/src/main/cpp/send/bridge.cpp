#include <jni.h>
#include <vector>
#include "send.hpp"

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_lumitalk_NativeBridge_generateSignalSequence(
        JNIEnv* env,
        jobject /* this */,
        jstring data,
        jint T) {

    const char* dataStr = env->GetStringUTFChars(data, nullptr);
    std::string dataStdStr(dataStr);
    env->ReleaseStringUTFChars(data, dataStr);

    std::vector<Signal> seq = generateSequence(dataStdStr, T);

    jintArray result = env->NewIntArray(seq.size() * 2);
    std::vector<jint> flat;
    for (const auto& s : seq) {
        flat.push_back(s.state);
        flat.push_back(s.duration);
    }
    env->SetIntArrayRegion(result, 0, flat.size(), flat.data());

    return result;
}
