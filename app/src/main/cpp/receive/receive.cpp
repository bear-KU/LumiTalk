#include "receive.hpp"
#include <opencv2/opencv.hpp>
#include <string>

std::string processFrame(const unsigned char* data, int width, int height) {
    cv::Mat gray(height, width, CV_8UC1, const_cast<unsigned char*>(data));
    
    cv::Mat thresh;
    cv::threshold(gray, thresh, 200.0, 255, cv::THRESH_BINARY);
    
    int nonZero = cv::countNonZero(thresh);
    
    return "bright pixels: " + std::to_string(nonZero);
}
