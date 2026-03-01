#pragma once

#include <string>
#include <vector>

struct Signal {
    int state;    // 1=ON, 0=OFF
    int duration; // ms
};

std::vector<Signal> generateSequence(const std::string& data, int T);
