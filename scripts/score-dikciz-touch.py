#!/usr/bin/env python3
"""Score touch traces from the Dikciz fixture app for scripted-input tells.

The fixture's FixtureAccessibilityActivity logs one DikcizTouchTrace line per
completed touch. Feed this script a logcat dump of those lines; it prints a
JSON report and exits 1 when the session looks scripted. The checks are the
cheap ones a bot detector runs first, with thresholds set from Touchalytics
(Frank et al. 2013), see scripts/fit-dikciz-touch-model.py:

  - most swipes move at one constant speed from start to end, or on a perfect straight line;
  - every tap lands on the same pixel, or exactly on the target centre;
  - every tap or press lasts the same time, or the double-tap gap never varies;
  - a tap lasts outside what people do.

    adb logcat -d -s DikcizTouchTrace:I > touches.log
    python3 scripts/score-dikciz-touch.py touches.log

A pass means none of these tells fired. It does not prove a touch is human:
a determined detector also has Android's input verification and many more
features.
"""

import argparse
import json
import math
import re
import statistics
import sys

TRACE_PATTERN = re.compile(r"down_ms=(\d+) pointers=(\d+)(?: dpi=(\d+))? target=(\S+) samples=(\S*)")
# Touchalytics counts a press as a tap up to 12 px of roll on ~245 dpi phones.
TAP_MAX_TRAVEL_MM = 1.24
MM_PER_INCH = 25.4
DEFAULT_DPI = 160
TAP_MAX_MS = 600
HUMAN_TAP_MIN_MS = 25
MIN_SWIPE_SAMPLES = 6
# Touchalytics: p10 of max deviation is 0.0197 of the chord; a line far below that on
# most swipes is drawn, not swiped.
STRAIGHT_DEVIATION_SHARE = 0.004
# A finger speeds up after it goes down and is usually slowing when it lifts. Mean
# speed over the first and last 15 % of the time is compared with the middle 40 %.
# Touchalytics: 0.08 % of swipes stay within 5 % of the middle speed at both ends;
# a constant-speed injected stroke always does.
FLAT_SPEED_TOLERANCE = 0.05
SPEED_SMOOTHING_SAMPLES = 3
REPEAT_TOLERANCE_MS = 1
SAME_PIXEL_PX = 0.5
CENTRE_PX = 0.5
MIN_REPEATS = 3
DOUBLE_TAP_MAX_GAP_MS = 300


def parse(lines):
    touches = []
    for line in lines:
        match = TRACE_PATTERN.search(line)
        if not match:
            continue
        samples = []
        for part in match.group(5).split(","):
            if part:
                t, x, y = part.split(":")
                samples.append((int(t), float(x), float(y)))
        if not samples:
            continue
        target = None
        if match.group(4) != "none":
            left, top, right, bottom = (int(v) for v in match.group(4).split(":"))
            target = (left, top, right, bottom)
        touches.append({
            "down": int(match.group(1)),
            "pointers": int(match.group(2)),
            "dpi": int(match.group(3) or DEFAULT_DPI),
            "target": target,
            "samples": samples,
        })
    return touches


def travel(samples):
    return math.dist(samples[0][1:], samples[-1][1:])


def duration(samples):
    return samples[-1][0] - samples[0][0]


def max_deviation_share(samples):
    (_, x0, y0), (_, x1, y1) = samples[0], samples[-1]
    chord = math.hypot(x1 - x0, y1 - y0)
    if chord == 0:
        return 0.0
    return max(abs((x1 - x0) * (y0 - y) - (x0 - x) * (y1 - y0)) / chord for _, x, y in samples) / chord


def speed_variation(samples):
    """Coefficient of variation of smoothed speed over the middle 80 % of the swipe."""
    total = duration(samples)
    speeds = [
        ((a[0] + b[0]) / 2 - samples[0][0], math.dist(a[1:], b[1:]) / (b[0] - a[0]))
        for a, b in zip(samples, samples[1:]) if b[0] > a[0]
    ]
    width = SPEED_SMOOTHING_SAMPLES
    smoothed = [
        (speeds[i + width // 2][0], statistics.fmean(v for _, v in speeds[i:i + width]))
        for i in range(len(speeds) - width + 1)
    ]
    middle = [v for t, v in smoothed if 0.1 * total <= t <= 0.9 * total]
    if len(middle) < 3 or statistics.fmean(middle) == 0:
        return None
    return statistics.pstdev(middle) / statistics.fmean(middle)


def inside(point, rect):
    return rect[0] <= point[0] <= rect[2] and rect[1] <= point[1] <= rect[3]


def end_speed_ratios(samples):
    """Mean speed over the first and last 15 % of the time, relative to the middle 40 %."""
    start = samples[0][0]
    total = duration(samples)

    def mean_speed(t0, t1):
        a = min(samples, key=lambda p: abs(p[0] - start - t0 * total))
        b = min(samples, key=lambda p: abs(p[0] - start - t1 * total))
        return math.dist(a[1:], b[1:]) / max(1, b[0] - a[0])
    middle = mean_speed(0.3, 0.7)
    if middle == 0:
        return None
    return mean_speed(0.0, 0.15) / middle, mean_speed(0.85, 1.0) / middle


def all_same(values, tolerance):
    return len(values) >= MIN_REPEATS and max(values) - min(values) <= tolerance


def score(touches):
    single = [t for t in touches if t["pointers"] == 1]
    taps = [
        t for t in single
        if travel(t["samples"]) * MM_PER_INCH / t["dpi"] <= TAP_MAX_TRAVEL_MM and duration(t["samples"]) <= TAP_MAX_MS
    ]
    swipes = [t for t in single if t not in taps and len(t["samples"]) >= MIN_SWIPE_SAMPLES]
    tells = []

    straight = [s for s in swipes if max_deviation_share(s["samples"]) < STRAIGHT_DEVIATION_SHARE]
    if swipes and len(straight) * 2 > len(swipes):
        tells.append(f"{len(straight)} of {len(swipes)} swipes run on a ruler-straight line")
    variations = [v for v in (speed_variation(s["samples"]) for s in swipes) if v is not None]
    ends = [r for r in (end_speed_ratios(s["samples"]) for s in swipes) if r is not None]
    flat = [r for r in ends if all(abs(x - 1) <= FLAT_SPEED_TOLERANCE for x in r)]
    if ends and len(flat) * 2 > len(ends):
        tells.append(f"{len(flat)} of {len(ends)} swipes move at one constant speed from start to end")

    tap_points = [t["samples"][0][1:] for t in taps]
    if len(tap_points) >= MIN_REPEATS and all(math.dist(p, tap_points[0]) <= SAME_PIXEL_PX for p in tap_points):
        tells.append(f"all {len(taps)} taps land on the same pixel")
    on_target = [t for t in taps if t["target"] and inside(t["samples"][0][1:], t["target"])]
    centred = [
        t for t in on_target if math.dist(
            t["samples"][0][1:],
            ((t["target"][0] + t["target"][2]) / 2, (t["target"][1] + t["target"][3]) / 2),
        ) <= CENTRE_PX
    ]
    if len(on_target) >= MIN_REPEATS and len(centred) == len(on_target):
        tells.append(f"all {len(centred)} taps on the button hit its exact centre")
    tap_durations = [duration(t["samples"]) for t in taps]
    if all_same(tap_durations, REPEAT_TOLERANCE_MS):
        tells.append(f"all {len(taps)} taps last {tap_durations[0]} ms")
    short = [d for d in tap_durations if d < HUMAN_TAP_MIN_MS]
    if short:
        tells.append(f"{len(short)} taps shorter than {HUMAN_TAP_MIN_MS} ms")
    swipe_durations = [duration(s["samples"]) for s in swipes]
    if all_same(swipe_durations, REPEAT_TOLERANCE_MS):
        tells.append(f"all {len(swipes)} swipes last {swipe_durations[0]} ms")

    ordered = sorted(taps, key=lambda t: t["down"])
    gaps = [
        b["down"] - (a["down"] + duration(a["samples"]))
        for a, b in zip(ordered, ordered[1:])
        if 0 <= b["down"] - (a["down"] + duration(a["samples"])) <= DOUBLE_TAP_MAX_GAP_MS
    ]
    # Separate taps can also fall within the window, so look for one exact gap that
    # repeats across at least half of them rather than requiring every gap to match.
    repeated = max(gaps, key=lambda g: sum(abs(o - g) <= REPEAT_TOLERANCE_MS for o in gaps), default=None)
    count = sum(abs(o - repeated) <= REPEAT_TOLERANCE_MS for o in gaps) if gaps else 0
    if count >= 2 and count * 2 >= len(gaps):
        tells.append(f"{count} of {len(gaps)} double-tap gaps are exactly {repeated} ms")

    return {
        "touches": len(touches),
        "taps": len(taps),
        "swipes": len(swipes),
        "multi_finger": len(touches) - len(single),
        "tap_ms": summary(tap_durations),
        "swipe_ms": summary(swipe_durations),
        "swipe_deviation_share": summary([round(max_deviation_share(s["samples"]), 4) for s in swipes]),
        "swipe_speed_variation": summary([round(v, 3) for v in variations]),
        "swipe_start_speed_ratio": summary([round(r[0], 3) for r in ends]),
        "swipe_end_speed_ratio": summary([round(r[1], 3) for r in ends]),
        "double_tap_gap_ms": summary(gaps),
        "tells": tells,
        "verdict": "scripted" if tells else "pass",
    }


def summary(values):
    if not values:
        return None
    return {"min": min(values), "median": statistics.median(values), "max": max(values), "n": len(values)}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("log", help="logcat dump holding DikcizTouchTrace lines, or - for stdin")
    args = parser.parse_args()
    stream = sys.stdin if args.log == "-" else open(args.log, encoding="utf-8", errors="replace")
    with stream:
        report = score(parse(stream))
    print(json.dumps(report, indent=2))
    return 1 if report["tells"] else 0


if __name__ == "__main__":
    sys.exit(main())
