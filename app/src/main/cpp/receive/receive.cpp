#include "receive.hpp"
#include "tracking.hpp"
#include <list>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "receive"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

const double DETECTION_THRESHOLD = 200.0;
const double MIN_CONTOUR_AREA = 1000.0;
const double MAX_CONTOUR_AREA = 100000.0;
const int MISS_COUNT_FOR_DELETION = 50;

static std::list<Tracker> activeTrackers;
static int tracker_id_counter = 0;
static std::mutex trackers_mutex;

static std::vector<DetectionResult> decodedResults;
static std::mutex results_mutex;

struct Detection
{
    cv::Rect box;
    double area;
};

static std::vector<Detection> detect(const cv::Mat& gray)
{
    cv::Mat thresh;

    // DEBUG
    // int cx = gray.cols / 2;
    // int cy = gray.rows / 2;
    // int region = 10; // 中央±10ピクセルの領域
    // cv::Rect roi(cx - region, cy - region, region * 2, region * 2);
    // cv::Mat center_region = gray(roi);
    // double mean_val = cv::mean(center_region)[0];
    // double min_val, max_val;
    // cv::minMaxLoc(center_region, &min_val, &max_val);
    // __android_log_print(ANDROID_LOG_INFO, "receive", "Y center mean=%.1f min=%.1f max=%.1f", mean_val, min_val, max_val);

    cv::threshold(gray, thresh, DETECTION_THRESHOLD, 255, cv::THRESH_BINARY);

    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5));
    cv::morphologyEx(thresh, thresh, cv::MORPH_OPEN, kernel);
    cv::morphologyEx(thresh, thresh, cv::MORPH_CLOSE, kernel);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(thresh, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    std::vector<Detection> detections;
    for (const auto& contour : contours)
    {
        double area = cv::contourArea(contour);
        if (area >= MIN_CONTOUR_AREA && area <= MAX_CONTOUR_AREA)
        {
            Detection det;
            det.box = cv::boundingRect(contour);
            det.area = area;
            detections.push_back(det);
        }
    }
    return detections;
}

std::string processFrame(const unsigned char* data, int width, int height)
{
    cv::Mat gray(height, width, CV_8UC1, const_cast<unsigned char*>(data));

    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);

    std::vector<Detection> detections = detect(blurred);
    std::vector<bool> matched(detections.size(), false);

    std::lock_guard<std::mutex> lock(trackers_mutex);

    for (auto& tracker : activeTrackers)
    {
        int best_index = -1;
        double best_dist = 100.0;
        for (size_t i = 0; i < detections.size(); ++i)
        {
            if (matched[i]) continue;
            cv::Point2f center(
                detections[i].box.x + detections[i].box.width / 2.0f,
                detections[i].box.y + detections[i].box.height / 2.0f
            );
            double dist = cv::norm(tracker.pos - center);
            if (dist < best_dist)
            {
                best_dist = dist;
                best_index = i;
            }
        }

        if (best_index != -1)
        {
            tracker.pos = cv::Point2f(
                detections[best_index].box.x + detections[best_index].box.width / 2.0f,
                detections[best_index].box.y + detections[best_index].box.height / 2.0f
            );
            tracker.size = detections[best_index].box.size();
            tracker.miss_count = 0;
            matched[best_index] = true;

            int x = std::clamp((int)tracker.pos.x, 0, width - 1);
            int y = std::clamp((int)tracker.pos.y, 0, height - 1);
            tracker.frame_queue.push({false, true, gray.at<uchar>(y, x), tracker.pos, detections[best_index].area});
        }
        else
        {
            tracker.miss_count++;
            tracker.frame_queue.push({false, false, 0, tracker.pos, 0.0});
        }
    }

    activeTrackers.remove_if([](Tracker& t)
    {
        if (t.miss_count > MISS_COUNT_FOR_DELETION)
        {
            t.frame_queue.push({true});
            return true;
        }
        return false;
    });

    for (size_t i = 0; i < detections.size(); ++i)
    {
        if (!matched[i])
        {
            activeTrackers.emplace_back();
            Tracker& new_tracker = activeTrackers.back();
            new_tracker.start_time = std::chrono::high_resolution_clock::now();
            new_tracker.id = tracker_id_counter++;
            new_tracker.pos = cv::Point2f(
                detections[i].box.x + detections[i].box.width / 2.0f,
                detections[i].box.y + detections[i].box.height / 2.0f
            );
            new_tracker.size = detections[i].box.size();
            new_tracker.worker = std::thread(
                trackerThreadFunction,
                new_tracker.id,
                &new_tracker.frame_queue,
                &new_tracker,
                [](int id, cv::Point2f pos, std::string ascii)
                {
                    std::lock_guard<std::mutex> lock(results_mutex);
                    decodedResults.push_back({id, pos, ascii});
                    __android_log_print(ANDROID_LOG_INFO, "receive",
                        "Decoded: id=%d ascii=%s", id, ascii.c_str());
                }
            );
        }
    }

    std::lock_guard<std::mutex> results_lock(results_mutex);

    // 毎フレーム: アクティブトラッカーのバウンディングボックスを返す
    std::string boxes_json = "[";
    bool first_box = true;

    for (const auto& tracker : activeTrackers)
    {
        if (!first_box) boxes_json += ",";
        int bx = (int)(tracker.pos.x - tracker.size.width / 2.0f);
        int by = (int)(tracker.pos.y - tracker.size.height / 2.0f);
        boxes_json += "{\"x\":" + std::to_string(bx)
                    + ",\"y\":" + std::to_string(by)
                    + ",\"w\":" + std::to_string((int)tracker.size.width)
                    + ",\"h\":" + std::to_string((int)tracker.size.height) + "}";
        first_box = false;
    }
    boxes_json += "]";

    // デコード済み結果
    std::string decoded_json = "[";
    bool first_dec = true;
    for (const auto& r : decodedResults)
    {
        if (r.ascii.empty()) continue;
        if (!first_dec) decoded_json += ",";
        decoded_json += "{\"x\":" + std::to_string((int)r.pos.x)
                      + ",\"y\":" + std::to_string((int)r.pos.y)
                      + ",\"ascii\":\"" + r.ascii + "\"}";
        first_dec = false;
    }
    decoded_json += "]";
    decodedResults.clear();

    return "{\"boxes\":" + boxes_json + ",\"decoded\":" + decoded_json + "}";
}
