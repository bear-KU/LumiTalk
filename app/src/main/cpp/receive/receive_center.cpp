#include "receive_center.hpp"
#include "tracking.hpp"
#include <thread>
#include <mutex>
#include <condition_variable>
#include <vector>
#include <android/log.h>

#define LOG_TAG "receive_center"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static const int RING_BUFFER_SIZE = 128;

struct RoiFrame {
    std::vector<uint8_t> data;
    int width = 0, height = 0;
};

static RoiFrame            ring_buffer[RING_BUFFER_SIZE];
static int                 write_idx   = 0;
static int                 read_idx    = 0;
static int                 frame_count = 0;
static std::mutex              ring_mutex;
static std::condition_variable ring_cv;

static std::string latest_result;
static std::mutex  result_mutex;

static bool        current_state = false;
static std::mutex  state_mutex; 

static std::thread worker_thread;
static bool        running = false;

static void workerFunc()
{
    std::vector<std::pair<bool, int>> states;
    bool   last_state    = false;
    int    state_counter = 0;
    double T_frames      = 0.0;
    bool   leader_found  = false;

    while (running)
    {
        RoiFrame frame;
        {
            std::unique_lock<std::mutex> lock(ring_mutex);
            ring_cv.wait(lock, [] { return frame_count > 0 || !running; });
            if (!running) break;
            frame       = ring_buffer[read_idx];
            read_idx    = (read_idx + 1) % RING_BUFFER_SIZE;
            frame_count--;
            // LOGI("worker: frame_count=%d", frame_count);
        }

        uint8_t intensity = frame.data[(frame.height / 2) * frame.width + (frame.width / 2)];
        bool current_on = intensity > STATE_CHANGE_THRESHOLD;

        if (current_on != last_state)
        {
            if (state_counter > 0)
                states.push_back({last_state, state_counter});
            state_counter = 0;
            last_state    = current_on;
        }
        state_counter++;

        if (!leader_found)
        {
            for (const auto& s : states)
            {
                if (s.first && s.second >= 8)
                {
                    LOGI("Leader found: T_frames=%.2d", s.second);
                    T_frames     = s.second / 8.0;
                    leader_found = true;
                    break;
                }
            }
        }

        if (leader_found && T_frames > 0.0 && current_on)
        {
            double ratio = state_counter / T_frames;
            if (ratio >= 3.5) // フレームドロップを考慮して，3.5 T 以上をトレイラとみなす
            {
                LOGI("Trailer detected: ratio=%.2f", ratio);
                states.push_back({last_state, state_counter});

                DecodeResult result = decodeFromStates(0, T_frames, states);
                LOGI("Decoded: bits=%s ascii=%s", result.bits.c_str(), result.ascii.c_str());

                if (!result.ascii.empty())
                {
                    std::lock_guard<std::mutex> lock(result_mutex);
                    latest_result = result.ascii;
                }

                states.clear();
                last_state    = false;
                state_counter = 0;
                T_frames      = 0.0;
                leader_found  = false;
            }
        }
        if (leader_found && T_frames > 0.0 && !current_on)
        {
            double ratio = state_counter / T_frames;
            if (ratio >= 10.0)
            {
                LOGI("Timeout fallback: ratio=%.2f", ratio);
                states.push_back({last_state, state_counter});

                DecodeResult result = decodeFromStates(0, T_frames, states);
                LOGI("Decoded: bits=%s ascii=%s", result.bits.c_str(), result.ascii.c_str());

                if (!result.ascii.empty())
                {
                    std::lock_guard<std::mutex> lock(result_mutex);
                    latest_result = result.ascii;
                }

                states.clear();
                last_state    = false;
                state_counter = 0;
                T_frames      = 0.0;
                leader_found  = false;
            }
        }
    }
}

void initReceiveCenter()
{
    if (running) return;
    running       = true;
    write_idx     = 0;
    read_idx      = 0;
    frame_count   = 0;
    worker_thread = std::thread(workerFunc);
}

void stopReceiveCenter()
{
    running = false;
    ring_cv.notify_all();
    if (worker_thread.joinable())
        worker_thread.join();
}

void pushFrameCenter(const uint8_t* data, int width, int height)
{
    static int fps_count = 0;
    static auto fps_last = std::chrono::high_resolution_clock::now();
    fps_count++;
    auto fps_now = std::chrono::high_resolution_clock::now();
    double elapsed = std::chrono::duration<double>(fps_now - fps_last).count();
    if (elapsed >= 1.0) {
        LOGI("pushFrame FPS: %.1f", fps_count / elapsed);
        fps_count = 0;
        fps_last  = fps_now;
    }

    uint8_t intensity = data[(height / 2) * width + (width / 2)];
    // LOGI("intensity=%d threshold=%.0f state=%d", 
    //      intensity, STATE_CHANGE_THRESHOLD, intensity > STATE_CHANGE_THRESHOLD);
    {
        std::lock_guard<std::mutex> lock(state_mutex);
        current_state = intensity > STATE_CHANGE_THRESHOLD;
    }

    std::lock_guard<std::mutex> lock(ring_mutex);
    if (frame_count == RING_BUFFER_SIZE)
    {
        read_idx = (read_idx + 1) % RING_BUFFER_SIZE;
        frame_count--;
        LOGI("Ring buffer full, dropping oldest frame");
    }
    ring_buffer[write_idx].data.assign(data, data + width * height);
    ring_buffer[write_idx].width  = width;
    ring_buffer[write_idx].height = height;
    write_idx = (write_idx + 1) % RING_BUFFER_SIZE;
    frame_count++;
    ring_cv.notify_one();
}

std::string getResultCenter()
{
    std::lock_guard<std::mutex> lock(result_mutex);
    std::string result = latest_result;
    latest_result.clear();
    return result;
}

bool getStateCenter()
{
    std::lock_guard<std::mutex> lock(state_mutex);
    return current_state;
}
