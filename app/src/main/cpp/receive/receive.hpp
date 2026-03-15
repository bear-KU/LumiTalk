#pragma once

#include <string>
#include <functional>
#include <opencv2/opencv.hpp>

struct DetectionResult
{
    int tracker_id;
    cv::Point2f pos;
    std::string ascii;
};

std::string processFrame(const unsigned char* data, int width, int height);
