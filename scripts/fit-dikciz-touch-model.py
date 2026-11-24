#!/usr/bin/env python3
"""Fit Dikciz's humanized touch parameters from the Touchalytics raw touch data.

Touchalytics (Frank, Biedert, Ma, Martinovic and Song, 2013,
https://www.mariofrank.net/touchalytics/) recorded 41 people reading articles and
playing an image game on five Android phones. This script reads its raw archive
directly and prints only pooled parameters: no participant identifier, trace or
table leaves the archive. Dikciz ships the printed numbers as named constants in
DikcizTouchModel.kt, with this script as their source.

    python3 scripts/fit-dikciz-touch-model.py .research_files/mobile-input/touchalytics.zip

Phone IDs in the archive run 0 to 4 (its readme numbers them 1 to 5).

A moving stroke is every event between a touch down and a touch up with a
displacement; a click is one without. Strokes are kept when they last 40 ms to
3 s, travel at least 24 px with at least four move samples, and never jump faster
than SPEED_LIMIT_PX_PER_MS between two samples (sensor glitches on these phones).
"""

import argparse
import csv
import io
import json
import math
from statistics import median, pstdev
from zipfile import ZipFile

ARCHIVE_MEMBER = "data/data.csv"
DOWN, UP, MOVE = "0", "1", "2"
ROW_FIELDS = 11
MIN_STROKE_MS = 40
MAX_STROKE_MS = 3000
MIN_STROKE_PX = 24.0
MIN_MOVE_SAMPLES = 4
SPEED_LIMIT_PX_PER_MS = 12.0
MAX_CLICK_MS = 600
PEAK_SMOOTHING_SAMPLES = 3
# The five study phones are 480x800 screens of 233 to 252 dpi. Pixel distances that
# Dikciz uses are converted to millimetres at this nominal density.
NOMINAL_DPI = 245
MM_PER_INCH = 25.4


def strokes(archive_path):
    """Yield (kind, samples) per completed sequence; samples are (t_ms, x, y)."""
    pending = {}
    with ZipFile(archive_path) as archive, archive.open(ARCHIVE_MEMBER) as data:
        for row in csv.reader(io.TextIOWrapper(data, encoding="ascii")):
            if len(row) != ROW_FIELDS:
                raise ValueError("unexpected Touchalytics row shape")
            session = tuple(row[0:3])
            moment, action = int(row[3]), row[4]
            point = (moment, float(row[6]), float(row[7]))
            if action == DOWN:
                pending[session] = [point]
            elif session in pending:
                pending[session].append(point)
                if action == UP:
                    samples = pending.pop(session)
                    moved = math.dist(samples[0][1:], samples[-1][1:]) > 0
                    yield ("stroke" if moved else "click"), samples


def path_length(samples):
    return sum(math.dist(a[1:], b[1:]) for a, b in zip(samples, samples[1:]))


def max_deviation(samples):
    """Largest perpendicular distance from the chord, as a share of the chord."""
    (_, x0, y0), (_, x1, y1) = samples[0], samples[-1]
    chord = math.hypot(x1 - x0, y1 - y0)
    if chord == 0:
        return 0.0
    signed = [((x1 - x0) * (y0 - y) - (x0 - x) * (y1 - y0)) / chord for _, x, y in samples]
    return max(signed, key=abs) / chord


def peak_time_fraction(samples):
    """Where in the stroke the smoothed speed peaks, from 0 (start) to 1 (end)."""
    speeds = []
    for a, b in zip(samples, samples[1:]):
        dt = b[0] - a[0]
        if dt > 0:
            speeds.append(((a[0] + b[0]) / 2, math.dist(a[1:], b[1:]) / dt))
    if len(speeds) < PEAK_SMOOTHING_SAMPLES:
        return None
    smoothed = []
    for i in range(len(speeds) - PEAK_SMOOTHING_SAMPLES + 1):
        window = speeds[i:i + PEAK_SMOOTHING_SAMPLES]
        smoothed.append((window[len(window) // 2][0], sum(v for _, v in window) / len(window)))
    t_peak = max(smoothed, key=lambda item: item[1])[0]
    total = samples[-1][0] - samples[0][0]
    return (t_peak - samples[0][0]) / total if total > 0 else None


def usable(samples):
    duration = samples[-1][0] - samples[0][0]
    if not MIN_STROKE_MS <= duration <= MAX_STROKE_MS:
        return False
    if len(samples) - 2 < MIN_MOVE_SAMPLES or math.dist(samples[0][1:], samples[-1][1:]) < MIN_STROKE_PX:
        return False
    for a, b in zip(samples, samples[1:]):
        dt = b[0] - a[0]
        if dt > 0 and math.dist(a[1:], b[1:]) / dt > SPEED_LIMIT_PX_PER_MS:
            return False
    return True


def quantile(values, q):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, round(q * (len(ordered) - 1))))]


def spread(values, digits=None):
    """p10, median and p90, rounded to `digits` places when given."""
    def tidy(value):
        return value if digits is None else round(value, digits)
    return {"p10": tidy(quantile(values, 0.1)), "median": tidy(median(values)), "p90": tidy(quantile(values, 0.9))}


def lognormal(values):
    logs = [math.log(v) for v in values if v > 0]
    return {"mu": round(sum(logs) / len(logs), 4), "sigma": round(pstdev(logs), 4)}


def fit(archive_path):
    durations, chords, straightness, deviations, peaks, intervals = [], [], [], [], [], []
    click_durations, click_drift = [], []
    for kind, samples in strokes(archive_path):
        if kind == "click":
            duration = samples[-1][0] - samples[0][0]
            if 0 < duration <= MAX_CLICK_MS:
                click_durations.append(duration)
            continue
        duration = samples[-1][0] - samples[0][0]
        chord = math.dist(samples[0][1:], samples[-1][1:])
        if 0 < duration <= MAX_CLICK_MS and chord < MIN_STROKE_PX / 2:
            # A press that rolled a few pixels is a tap with drift, not a swipe.
            click_durations.append(duration)
            click_drift.append(chord)
            continue
        if not usable(samples):
            continue
        durations.append(duration)
        chords.append(chord)
        straightness.append(chord / path_length(samples))
        deviations.append(abs(max_deviation(samples)))
        peak = peak_time_fraction(samples)
        if peak is not None:
            peaks.append(peak)
        intervals.extend(b[0] - a[0] for a, b in zip(samples, samples[1:]) if b[0] > a[0])

    # Duration grows with distance: log(duration) = a + b * log(chord) + residual.
    xs = [math.log(c) for c in chords]
    ys = [math.log(d) for d in durations]
    mean_x, mean_y = sum(xs) / len(xs), sum(ys) / len(ys)
    slope = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys)) / sum((x - mean_x) ** 2 for x in xs)
    intercept = mean_y - slope * mean_x
    residual = pstdev(y - (intercept + slope * x) for x, y in zip(xs, ys))

    return {
        "source": "Touchalytics raw data, Frank et al. 2013",
        "strokes": len(durations),
        "clicks": len(click_durations),
        "swipe_duration_log_model": {
            "intercept": round(intercept, 4),
            "slope": round(slope, 4),
            "residual_sigma": round(residual, 4),
        },
        "swipe_duration_ms": spread(durations),
        "swipe_chord_px": spread(chords, 1),
        "swipe_straightness": spread(straightness, 4),
        "swipe_max_deviation_share": spread(deviations, 4),
        "swipe_peak_time_fraction": spread(peaks, 3),
        "move_sample_interval_ms": spread(intervals),
        "tap_duration_ms": {**spread(click_durations), **lognormal(click_durations)},
        "tap_drift_px": {
            "share_with_drift": round(len(click_drift) / len(click_durations), 4),
            **(spread(click_drift, 2) if click_drift else {}),
            "mm": spread([px * MM_PER_INCH / NOMINAL_DPI for px in click_drift], 2) if click_drift else {},
            "log_sigma": lognormal(click_drift)["sigma"] if click_drift else 0.0,
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("archive", help="path to the Touchalytics touchalytics.zip")
    args = parser.parse_args()
    print(json.dumps(fit(args.archive), indent=2))


if __name__ == "__main__":
    main()
