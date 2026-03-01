#include <jni.h>
#include <string>
#include <vector>

struct Signal {
    int state; // 1=ON, 0=OFF
    int duration; // ms
};

static std::vector<Signal> generateSequence(const std::string& data, int T) {
    std::vector<Signal> seq;

    auto leader = [&]() {
        seq.push_back({1, T * 8});
        seq.push_back({0, T * 4});
    };

    auto data0 = [&]() {
        seq.push_back({0, T * 1});
        seq.push_back({1, T * 1});
    };

    auto data1 = [&]() {
        seq.push_back({0, T * 1});
        seq.push_back({1, T * 2});
    };

    auto trailer = [&]() {
        seq.push_back({0, T * 1});
        seq.push_back({1, T * 5});
        seq.push_back({0, 0});
    };

    leader();
    for (char c : data) {
        for (int bit = 0; bit < 8; bit++) {
            if (c & (0x80 >> bit)) {
                data1();
            } else {
                data0();
            }
        }
    }
    trailer();

    return seq;
}

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

    // state と duration を交互に格納した配列として返す
    // [state0, duration0, state1, duration1, ...]
    jintArray result = env->NewIntArray(seq.size() * 2);
    std::vector<jint> flat;
    for (const auto& s : seq) {
        flat.push_back(s.state);
        flat.push_back(s.duration);
    }
    env->SetIntArrayRegion(result, 0, flat.size(), flat.data());

    return result;
}
