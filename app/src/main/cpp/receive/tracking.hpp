#pragma once

#include <string>
#include <bitset>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <vector>
#include <queue>
#include <optional>
#include <chrono>
#include <opencv2/opencv.hpp>

template <typename T>
class ThreadSafeQueue
{
public:
    void push(T value)
    {
        std::lock_guard<std::mutex> lock(mtx_);
        queue_.push(std::move(value));
        cond_var_.notify_one();
    }

    std::optional<T> pop()
    {
        std::unique_lock<std::mutex> lock(mtx_);
        cond_var_.wait(lock, [this] { return !queue_.empty(); });
        T value = std::move(queue_.front());
        queue_.pop();
        return value;
    }

private:
    std::queue<T> queue_;
    std::mutex mtx_;
    std::condition_variable cond_var_;
};

struct FrameUpdate
{
    bool terminate = false;
    bool found = false;
    int intensity = 0;
    cv::Point2f pos;
    double area = 0.0;
};

struct DecodeResult
{
    std::string bits;
    std::string ascii;
};

struct Tracker
{
    int id;
    cv::Point2f pos;
    cv::Size size;
    int miss_count = 0;

    std::thread worker;
    ThreadSafeQueue<FrameUpdate> frame_queue;

    std::chrono::high_resolution_clock::time_point start_time;
    std::chrono::high_resolution_clock::time_point end_time;

    ~Tracker();
    Tracker(Tracker&& other) noexcept;
    Tracker& operator=(Tracker&& other) noexcept;
    Tracker(const Tracker&) = delete;
    Tracker& operator=(const Tracker&) = delete;
    Tracker() = default;
};

const double STATE_CHANGE_THRESHOLD = 150.0;

extern std::mutex cout_mutex;

void trackerThreadFunction(int id, ThreadSafeQueue<FrameUpdate>* queue, Tracker* tracker_ptr, std::function<void(int, cv::Point2f, std::string)> onDecoded);

DecodeResult decodeFromStates(int id, double& T_frames, const std::vector<std::pair<bool, int>>& states);
std::string binaryToAscii(const std::string& binary);
