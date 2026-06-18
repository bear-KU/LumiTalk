#pragma once
#include <string>
#include <cstdint>

void initReceiveCenter();
void stopReceiveCenter();
void pushFrameCenter(const uint8_t* data, int width, int height);
std::string getResultCenter();
bool getStateCenter();
