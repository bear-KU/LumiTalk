#include "receive.hpp"
#include <string>

std::string processFrame(const unsigned char* data, int width, int height) {
    return "width: " + std::to_string(width) + ", height: " + std::to_string(height);
}
