#include "send.hpp"

static void leader(std::vector<Signal>& seq, int T) {
    seq.push_back({1, T * 8});
    seq.push_back({0, T * 4});
}

static void data0(std::vector<Signal>& seq, int T) {
    seq.push_back({0, T * 1});
    seq.push_back({1, T * 1});
}

static void data1(std::vector<Signal>& seq, int T) {
    seq.push_back({0, T * 1});
    seq.push_back({1, T * 2});
}

static void trailer(std::vector<Signal>& seq, int T) {
    seq.push_back({0, T * 1});
    seq.push_back({1, T * 5});
    seq.push_back({0, 0});
}

std::vector<Signal> generateSequence(const std::string& data, int T) {
    std::vector<Signal> seq;

    leader(seq, T);
    for (char c : data) {
        for (int bit = 0; bit < 8; bit++) {
            if (c & (0x80 >> bit)) {
                data1(seq, T);
            } else {
                data0(seq, T);
            }
        }
    }
    trailer(seq, T);

    return seq;
}
