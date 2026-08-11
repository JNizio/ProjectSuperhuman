#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <numeric>
#include <vector>

namespace {

constexpr std::size_t kHeaderSize = 16;
constexpr double kNaN = std::numeric_limits<double>::quiet_NaN();
constexpr std::int64_t kMinuteMs = 60'000;
constexpr std::int64_t kHourMs = 60 * kMinuteMs;
constexpr std::int64_t kDayMs = 24 * kHourMs;

struct Point {
    std::int64_t timeMs;
    double bpm;
};

double percentile(std::vector<double> values, double p) {
    if (values.empty()) return kNaN;
    std::sort(values.begin(), values.end());
    if (values.size() == 1) return values.front();
    const double index = std::clamp(p, 0.0, 1.0) * static_cast<double>(values.size() - 1);
    const auto low = static_cast<std::size_t>(std::floor(index));
    const auto high = static_cast<std::size_t>(std::ceil(index));
    if (low == high) return values[low];
    const double fraction = index - static_cast<double>(low);
    return values[low] * (1.0 - fraction) + values[high] * fraction;
}

double median(const std::vector<double>& values) {
    return percentile(values, 0.5);
}

std::vector<double> medianFilter(const std::vector<Point>& points) {
    std::vector<double> output(points.size());
    for (std::size_t i = 0; i < points.size(); ++i) {
        const std::size_t from = i > 2 ? i - 2 : 0;
        const std::size_t to = std::min(points.size() - 1, i + 2);
        std::vector<double> window;
        window.reserve(to - from + 1);
        for (std::size_t j = from; j <= to; ++j) window.push_back(points[j].bpm);
        output[i] = median(window);
    }
    return output;
}

std::vector<double> timeAwareEma(const std::vector<Point>& points, const std::vector<double>& filtered) {
    if (points.empty()) return {};
    std::vector<double> output(points.size());
    output[0] = filtered[0];
    constexpr double tauSeconds = 120.0;
    for (std::size_t i = 1; i < points.size(); ++i) {
        const double dtSeconds = std::clamp(
            static_cast<double>(points[i].timeMs - points[i - 1].timeMs) / 1000.0,
            1.0,
            3600.0
        );
        const double alpha = 1.0 - std::exp(-dtSeconds / tauSeconds);
        output[i] = output[i - 1] + alpha * (filtered[i] - output[i - 1]);
    }
    return output;
}

double robustBaseline(const std::vector<double>& values) {
    if (values.empty()) return kNaN;
    std::vector<double> sorted(values);
    std::sort(sorted.begin(), sorted.end());
    const std::size_t count = std::min(
        sorted.size(),
        std::max<std::size_t>(3, static_cast<std::size_t>(std::ceil(sorted.size() * 0.20)))
    );
    return median(std::vector<double>(sorted.begin(), sorted.begin() + count));
}

double stabilityScore(const std::vector<double>& values) {
    if (values.size() < 3) return kNaN;
    const double centre = median(values);
    if (!std::isfinite(centre) || centre <= 0.0) return kNaN;
    std::vector<double> deviations;
    deviations.reserve(values.size());
    for (const double value : values) deviations.push_back(std::abs(value - centre));
    const double mad = median(deviations);
    const double robustSigma = 1.4826 * mad;
    const double relativeNoise = robustSigma / centre;
    return std::clamp(100.0 - relativeNoise * 220.0, 0.0, 100.0);
}

double coveragePercent(const std::vector<Point>& points) {
    if (points.empty()) return 0.0;
    constexpr std::int64_t binMs = 15 * kMinuteMs;
    constexpr int binCount = 96;
    const std::int64_t windowEnd = points.back().timeMs;
    const std::int64_t windowStart = windowEnd - kDayMs;
    bool occupied[binCount] = {};
    for (const Point& point : points) {
        if (point.timeMs < windowStart || point.timeMs > windowEnd) continue;
        const std::int64_t raw = (point.timeMs - windowStart) / binMs;
        const int index = static_cast<int>(std::clamp<std::int64_t>(raw, 0, binCount - 1));
        occupied[index] = true;
    }
    int used = 0;
    for (const bool value : occupied) if (value) ++used;
    return static_cast<double>(used) / static_cast<double>(binCount) * 100.0;
}

int unusualSampleCount(const std::vector<Point>& points, const std::vector<double>& values) {
    if (values.size() < 3) return 0;
    const double centre = median(values);
    std::vector<double> deviations;
    deviations.reserve(values.size());
    for (const double value : values) deviations.push_back(std::abs(value - centre));
    const double robustSigma = 1.4826 * median(deviations);
    const double globalThreshold = std::max(18.0, robustSigma * 4.0);
    const double localThreshold = std::max(15.0, robustSigma * 3.0);

    // Only isolated/transient departures are flagged. A sustained elevated segment is
    // usually exercise or activity, not a sensor outlier, so it deliberately does not
    // become an "unusual sample" merely for sitting far above the daily median.
    int count = 0;
    for (std::size_t i = 1; i + 1 < values.size(); ++i) {
        const auto previousGap = points[i].timeMs - points[i - 1].timeMs;
        const auto nextGap = points[i + 1].timeMs - points[i].timeMs;
        if (previousGap <= 0 || nextGap <= 0) continue;

        const double neighbourCentre = (values[i - 1] + values[i + 1]) / 2.0;
        const double globalDeviation = std::abs(values[i] - centre);
        const double localDeviation = std::abs(values[i] - neighbourCentre);
        const bool neighboursAgree = std::abs(values[i - 1] - values[i + 1]) <= 16.0;
        const bool isolatedOutlier =
            previousGap <= 10 * kMinuteMs &&
            nextGap <= 10 * kMinuteMs &&
            neighboursAgree &&
            globalDeviation > globalThreshold &&
            localDeviation > localThreshold;

        const bool rapidTransient =
            previousGap <= 5 * kMinuteMs &&
            nextGap <= 5 * kMinuteMs &&
            std::abs(values[i] - values[i - 1]) >= 25.0 &&
            std::abs(values[i + 1] - values[i - 1]) <= 12.0;

        if (isolatedOutlier || rapidTransient) ++count;
    }
    return count;
}

double recentSlopeBpmPerHour(const std::vector<Point>& points, const std::vector<double>& values) {
    if (points.size() < 3 || values.size() != points.size()) return kNaN;
    const std::int64_t cutoff = points.back().timeMs - 6 * kHourMs;
    std::vector<std::size_t> indices;
    for (std::size_t i = 0; i < points.size(); ++i) {
        if (points[i].timeMs >= cutoff) indices.push_back(i);
    }
    if (indices.size() < 3) return kNaN;

    const double origin = static_cast<double>(points[indices.front()].timeMs);
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXX = 0.0;
    double sumXY = 0.0;
    for (const std::size_t index : indices) {
        const double x = (static_cast<double>(points[index].timeMs) - origin) / static_cast<double>(kHourMs);
        const double y = values[index];
        sumX += x;
        sumY += y;
        sumXX += x * x;
        sumXY += x * y;
    }
    const double n = static_cast<double>(indices.size());
    const double denominator = n * sumXX - sumX * sumX;
    if (std::abs(denominator) < 1e-9) return kNaN;
    return std::clamp((n * sumXY - sumX * sumY) / denominator, -30.0, 30.0);
}

double observedRecoveryDrop(const std::vector<Point>& points, const std::vector<double>& values, double baseline) {
    if (points.size() < 3 || values.size() != points.size() || !std::isfinite(baseline)) return kNaN;
    const auto peakIt = std::max_element(values.begin(), values.end());
    if (peakIt == values.end()) return kNaN;
    const std::size_t peakIndex = static_cast<std::size_t>(std::distance(values.begin(), peakIt));
    const double peak = *peakIt;
    if (peak < baseline + 30.0) return kNaN;

    const std::int64_t from = points[peakIndex].timeMs + 2 * kMinuteMs;
    const std::int64_t to = points[peakIndex].timeMs + 12 * kMinuteMs;
    double lowest = std::numeric_limits<double>::infinity();
    bool found = false;
    for (std::size_t i = peakIndex + 1; i < points.size(); ++i) {
        if (points[i].timeMs < from) continue;
        if (points[i].timeMs > to) break;
        lowest = std::min(lowest, values[i]);
        found = true;
    }
    if (!found || !std::isfinite(lowest)) return kNaN;
    return std::max(0.0, peak - lowest);
}

void intensityBands(
    const std::vector<double>& values,
    double baseline,
    double p95,
    double& low,
    double& moderate,
    double& elevated,
    double& high
) {
    low = moderate = elevated = high = 0.0;
    if (values.empty() || !std::isfinite(baseline) || !std::isfinite(p95)) return;
    const double span = std::max(20.0, p95 - baseline);
    const double t1 = baseline + span * 0.25;
    const double t2 = baseline + span * 0.55;
    const double t3 = baseline + span * 0.80;
    for (const double value : values) {
        if (value <= t1) ++low;
        else if (value <= t2) ++moderate;
        else if (value <= t3) ++elevated;
        else ++high;
    }
    const double n = static_cast<double>(values.size());
    low = low / n * 100.0;
    moderate = moderate / n * 100.0;
    elevated = elevated / n * 100.0;
    high = high / n * 100.0;
}

}  // namespace

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_projectsuperhuman_next_HeartRateNativeEngine_analyzeNative(
    JNIEnv* env,
    jobject /* thiz */,
    jlongArray timestampsMs,
    jdoubleArray bpmValues
) {
    if (timestampsMs == nullptr || bpmValues == nullptr) return nullptr;

    const jsize timestampCount = env->GetArrayLength(timestampsMs);
    const jsize bpmCount = env->GetArrayLength(bpmValues);
    const jsize inputCount = std::min(timestampCount, bpmCount);

    std::vector<jlong> rawTimes(static_cast<std::size_t>(inputCount));
    std::vector<jdouble> rawBpms(static_cast<std::size_t>(inputCount));
    if (inputCount > 0) {
        env->GetLongArrayRegion(timestampsMs, 0, inputCount, rawTimes.data());
        env->GetDoubleArrayRegion(bpmValues, 0, inputCount, rawBpms.data());
    }

    std::vector<Point> points;
    points.reserve(static_cast<std::size_t>(inputCount));
    for (jsize i = 0; i < inputCount; ++i) {
        const double bpm = rawBpms[static_cast<std::size_t>(i)];
        const auto time = static_cast<std::int64_t>(rawTimes[static_cast<std::size_t>(i)]);
        if (time > 0 && std::isfinite(bpm) && bpm >= 25.0 && bpm <= 260.0) {
            points.push_back({time, bpm});
        }
    }
    std::sort(points.begin(), points.end(), [](const Point& a, const Point& b) {
        return a.timeMs < b.timeMs;
    });

    std::vector<double> output(kHeaderSize + points.size(), kNaN);
    output[14] = static_cast<double>(points.size());

    if (!points.empty()) {
        const auto medianed = medianFilter(points);
        const auto smoothed = timeAwareEma(points, medianed);
        const double baseline = robustBaseline(smoothed);
        const double mean = std::accumulate(smoothed.begin(), smoothed.end(), 0.0) /
            static_cast<double>(smoothed.size());
        const double minValue = *std::min_element(smoothed.begin(), smoothed.end());
        const double maxValue = *std::max_element(smoothed.begin(), smoothed.end());
        const double p95 = percentile(smoothed, 0.95);
        const double stability = stabilityScore(smoothed);
        const double coverage = coveragePercent(points);
        const int unusual = unusualSampleCount(points, smoothed);
        const double slope = recentSlopeBpmPerHour(points, smoothed);
        const double recovery = observedRecoveryDrop(points, smoothed, baseline);
        double low = 0.0;
        double moderate = 0.0;
        double elevated = 0.0;
        double high = 0.0;
        intensityBands(smoothed, baseline, p95, low, moderate, elevated, high);

        output[0] = baseline;
        output[1] = mean;
        output[2] = minValue;
        output[3] = maxValue;
        output[4] = p95;
        output[5] = stability;
        output[6] = coverage;
        output[7] = static_cast<double>(unusual);
        output[8] = slope;
        output[9] = recovery;
        output[10] = low;
        output[11] = moderate;
        output[12] = elevated;
        output[13] = high;
        output[15] = smoothed.back();

        for (std::size_t i = 0; i < smoothed.size(); ++i) {
            output[kHeaderSize + i] = smoothed[i];
        }
    }

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(output.size()));
    if (result == nullptr) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(output.size()), output.data());
    return result;
}
