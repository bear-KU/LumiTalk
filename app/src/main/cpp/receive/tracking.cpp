#include "tracking.hpp"
#include <limits>
#include <android/log.h>

std::mutex cout_mutex;

Tracker::~Tracker()
{
    if (worker.joinable())
    {
        frame_queue.push({true});
        worker.join();
    }
}

Tracker::Tracker(Tracker&& other) noexcept
    : id(other.id),
      pos(other.pos),
      size(other.size),
      miss_count(other.miss_count),
      worker(std::move(other.worker)),
      start_time(other.start_time),
      end_time(other.end_time) {}

Tracker& Tracker::operator=(Tracker&& other) noexcept
{
    if (this != &other)
    {
        if (worker.joinable())
        {
            worker.join();
        }
        id = other.id;
        pos = other.pos;
        size = other.size;
        miss_count = other.miss_count;
        worker = std::move(other.worker);
        start_time = other.start_time;
        end_time = other.end_time;
    }
    return *this;
}

void trackerThreadFunction(int id, ThreadSafeQueue<FrameUpdate>* queue, Tracker* tracker_ptr, std::function<void(int, cv::Point2f, std::string)> onDecoded)
{
    std::vector<std::pair<bool, int>> states;
    bool last_state_is_on = false;
    int state_counter = 0;
    double T_frames = 0.0;

    while (true)
    {
        auto update_opt = queue->pop();
        if (!update_opt.has_value()) continue;

        FrameUpdate update = update_opt.value();
        if (update.terminate) break;

        bool current_state_is_on = update.found && (update.intensity > STATE_CHANGE_THRESHOLD);

        if (current_state_is_on != last_state_is_on)
        {
            if (state_counter > 0)
            {
                states.push_back({last_state_is_on, state_counter});
            }
            state_counter = 0;
            last_state_is_on = current_state_is_on;
        }
        state_counter++;

        if (states.size() >= 2)
        {
            if (T_frames == 0.0)
            {
                for (size_t i = 0; i < states.size(); ++i)
                {
                    if (states[i].first && states[i].second >= 8)
                    {
                        T_frames = static_cast<double>(states[i].second) / 8.0;
                        break;
                    }
                }
            }

            if (T_frames > 0.0 && current_state_is_on)
            {
                double ratio = state_counter / T_frames;
                if (ratio >= 5.0)
                {
                    states.push_back({last_state_is_on, state_counter});
                    break;
                }
            }
        }
    }

    if (state_counter > 0)
    {
        states.push_back({last_state_is_on, state_counter});
    }

    tracker_ptr->end_time = std::chrono::high_resolution_clock::now();

    DecodeResult result = decodeFromStates(id, T_frames, states);

    // DEBUG
    float cx = 1920 / 2.0f;
    float cy = 1080 / 2.0f;
    float dx = tracker_ptr->pos.x - cx;
    float dy = tracker_ptr->pos.y - cy;
    if (dx*dx + dy*dy < 300*300) {
        __android_log_print(ANDROID_LOG_INFO, "receive",
            "Center tracker id=%d pos=(%.0f,%.0f) bits=%s",
            id, tracker_ptr->pos.x, tracker_ptr->pos.y, result.bits.c_str());
    }

    if (!result.ascii.empty())
    {
        onDecoded(id, tracker_ptr->pos, result.ascii);
    }
}

std::string binaryToAscii(const std::string& binary)
{
    std::string result;
    if (binary.empty()) return result;
    size_t parsable_length = binary.size() - (binary.size() % 8);
    for (size_t i = 0; i < parsable_length; i += 8)
    {
        std::bitset<8> bits(binary.substr(i, 8));
        char c = static_cast<char>(bits.to_ulong());
        if (c < 0x20 || c > 0x7E) return "";
        result += c;
    }
    return result;
}

DecodeResult decodeFromStates(int id, double& T_frames, const std::vector<std::pair<bool, int>>& states)
{
    DecodeResult result;
    if (states.size() < 2) return result;

    int first_on_index = -1;
    for (size_t i = 0; i < states.size(); ++i)
    {
        if (states[i].first && states[i].second >= 8)
        {
            T_frames = static_cast<double>(states[i].second) / 8.0;
            first_on_index = static_cast<int>(i);
            break;
        }
    }

    if (first_on_index == -1) return result;

    for (size_t i = first_on_index + 1; i < states.size(); ++i)
    {
        const auto& state = states[i];
        if (!state.first) continue;
        double ratio = state.second / T_frames;
        if (ratio < 1.8) result.bits += "0";
        else if (ratio >= 1.8 && ratio < 5.0) result.bits += "1";
        else if (ratio >= 5.0) break;
    }
    result.ascii = binaryToAscii(result.bits);
    return result;
}
