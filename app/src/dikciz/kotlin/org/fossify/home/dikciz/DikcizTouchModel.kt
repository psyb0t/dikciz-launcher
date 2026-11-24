package org.fossify.home.dikciz

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Plans humanized touch gestures as timed samples per finger.
 *
 * This file is pure Kotlin so the planner is seedable and unit-testable; the
 * accessibility service turns a plan into GestureDescription strokes. Numbers
 * marked "Touchalytics" come from `scripts/fit-dikciz-touch-model.py` run on the
 * Touchalytics raw data (Frank et al. 2013, https://www.mariofrank.net/touchalytics/):
 * only pooled parameters are kept, never traces. Tap landing bias follows
 * Henze, Rukzio and Boll, "100,000,000 Taps" (2011), which found taps land
 * below the target centre; double-tap timing stays inside Android's
 * GestureDetector window (40 to 300 ms from the first lift to the second press).
 */
/** A standard normal draw (Box-Muller). */
internal fun Random.gaussian(): Double {
    var u = 0.0
    while (u == 0.0) u = nextDouble()
    return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * nextDouble())
}

/** One slow speed-up and slow-down along a swipe: `cycles` waves over its whole length. */
internal data class DikcizSpeedWave(val cycles: Double, val phase: Double, val weight: Double)

internal data class DikcizTouchSample(val timeMilliseconds: Long, val x: Float, val y: Float)

/** One finger: when it goes down, relative to the gesture start, and its path. */
internal data class DikcizPlannedPointer(
    val startMilliseconds: Long,
    val samples: List<DikcizTouchSample>,
) {
    val endMilliseconds: Long get() = startMilliseconds + samples.last().timeMilliseconds
    val moves: Boolean get() = samples.any { it.x != samples[0].x || it.y != samples[0].y }
}

/** The planned gesture. `continuous` pointers need speed changes along their path. */
internal data class DikcizTouchPlan(
    val pointers: List<DikcizPlannedPointer>,
    val continuous: Boolean,
) {
    val totalDurationMilliseconds: Long get() = pointers.maxOf { it.endMilliseconds }
}

internal data class DikcizTouchTarget(val x: Int, val y: Int, val width: Int?, val height: Int?)

/**
 * The per-person parameters, drawn once per process from the population so one
 * "person" taps and swipes consistently instead of drawing every gesture from
 * the whole population.
 */
internal class DikcizTouchPerson(random: Random) {
    val tapLogMedian: Double = DikcizTouchModel.TAP_LOG_MU + random.gaussian() * PERSON_TAP_LOG_SPREAD
    val tapLogSigma: Double = withinPerson(DikcizTouchModel.TAP_LOG_SIGMA, PERSON_TAP_LOG_SPREAD)
    val driftShare: Double = (DikcizTouchModel.TAP_DRIFT_SHARE + random.gaussian() * PERSON_DRIFT_SHARE_SPREAD)
        .coerceIn(MIN_DRIFT_SHARE, MAX_DRIFT_SHARE)
    val driftScale: Double = exp(random.gaussian() * PERSON_DRIFT_LOG_SPREAD)
    val curvatureScale: Double = exp(random.gaussian() * PERSON_CURVATURE_LOG_SPREAD)
    val peakShift: Double = random.gaussian() * PERSON_PEAK_SPREAD
    val doubleTapGapLogMedian: Double = ln(DikcizTouchModel.DOUBLE_TAP_GAP_MEDIAN_MILLISECONDS) +
        random.gaussian() * PERSON_GAP_LOG_SPREAD

    companion object {
        /**
         * The spread left for one person's own gestures, so that person-to-person
         * variation plus within-person variation adds up to the measured population
         * spread instead of exceeding it.
         */
        fun withinPerson(population: Double, betweenPeople: Double): Double =
            sqrt(population * population - betweenPeople * betweenPeople)

        const val PERSON_TAP_LOG_SPREAD = 0.2
        const val PERSON_DRIFT_SHARE_SPREAD = 0.08
        const val MIN_DRIFT_SHARE = 0.1
        const val MAX_DRIFT_SHARE = 0.6
        const val PERSON_DRIFT_LOG_SPREAD = 0.25
        const val PERSON_CURVATURE_LOG_SPREAD = 0.25
        const val PERSON_PEAK_SPREAD = 0.06
        const val PERSON_GAP_LOG_SPREAD = 0.15
    }
}

internal class DikcizTouchModel(
    private val random: Random,
    private val person: DikcizTouchPerson = DikcizTouchPerson(random),
    private val pixelsPerMillimetre: Float,
) {
    fun tap(target: DikcizTouchTarget, durationMilliseconds: Long?): DikcizTouchPlan {
        val press = durationMilliseconds?.let { jitter(it, PRESS_JITTER_LOG_SIGMA) } ?: drawTapPress()
        return DikcizTouchPlan(listOf(DikcizPlannedPointer(0, pressSamples(landing(target), press, person.driftShare))), false)
    }

    fun hold(target: DikcizTouchTarget, durationMilliseconds: Long?): DikcizTouchPlan {
        val press = jitter(durationMilliseconds ?: DEFAULT_HOLD_MILLISECONDS, PRESS_JITTER_LOG_SIGMA)
        return DikcizTouchPlan(listOf(DikcizPlannedPointer(0, pressSamples(landing(target), press, ALWAYS_DRIFT))), false)
    }

    fun doubleTap(target: DikcizTouchTarget, durationMilliseconds: Long?): DikcizTouchPlan {
        val firstPress = (durationMilliseconds?.let { jitter(it, PRESS_JITTER_LOG_SIGMA) } ?: drawDoubleTapPress())
        val secondPress = (durationMilliseconds?.let { jitter(it, PRESS_JITTER_LOG_SIGMA) } ?: drawDoubleTapPress())
        val gap = bounded(MIN_DOUBLE_TAP_GAP_MILLISECONDS.toDouble(), MAX_DOUBLE_TAP_GAP_MILLISECONDS.toDouble()) {
            exp(person.doubleTapGapLogMedian + random.gaussian() * DOUBLE_TAP_GAP_LOG_SIGMA)
        }.roundToInt().toLong()
        val first = landing(target)
        // The second press lands near the first, not on a fresh draw over the whole target.
        val second = clampToTarget(
            first.first + random.gaussian().toFloat() * SECOND_TAP_SPREAD_MM * pixelsPerMillimetre,
            first.second + random.gaussian().toFloat() * SECOND_TAP_SPREAD_MM * pixelsPerMillimetre,
            target,
        )
        return DikcizTouchPlan(
            listOf(
                DikcizPlannedPointer(0, pressSamples(first, firstPress, person.driftShare)),
                DikcizPlannedPointer(firstPress + gap, pressSamples(second, secondPress, person.driftShare)),
            ),
            false,
        )
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMilliseconds: Long?): DikcizTouchPlan {
        val duration = jitter(durationMilliseconds ?: DEFAULT_SWIPE_MILLISECONDS, SWIPE_DURATION_JITTER_LOG_SIGMA)
            .coerceAtLeast(MIN_SWIPE_MILLISECONDS)
        return DikcizTouchPlan(listOf(DikcizPlannedPointer(0, path(startX, startY, endX, endY, duration))), true)
    }

    fun twoFingerSwipe(
        first: IntArray,
        second: IntArray,
        durationMilliseconds: Long?,
    ): DikcizTouchPlan {
        val duration = jitter(durationMilliseconds ?: DEFAULT_TWO_FINGER_MILLISECONDS, SWIPE_DURATION_JITTER_LOG_SIGMA)
            .coerceAtLeast(MIN_SWIPE_MILLISECONDS)
        // People rarely put two fingers down in the same instant. The lag stays under one
        // dispatch slice: Android cannot add a new finger to a continued gesture.
        val lag = random.nextLong(0, MAX_SECOND_FINGER_LAG_MILLISECONDS + 1)
        val secondDuration = (duration - lag).coerceAtLeast(MIN_SWIPE_MILLISECONDS)
        return DikcizTouchPlan(
            listOf(
                DikcizPlannedPointer(0, path(first[0], first[1], first[2], first[3], duration)),
                DikcizPlannedPointer(lag, path(second[0], second[1], second[2], second[3], secondDuration)),
            ),
            true,
        )
    }

    private fun drawTapPress(): Long = bounded(MIN_TAP_MILLISECONDS.toDouble(), MAX_TAP_MILLISECONDS.toDouble()) {
        exp(person.tapLogMedian + random.gaussian() * person.tapLogSigma)
    }.roundToInt().toLong()

    private fun drawDoubleTapPress(): Long =
        bounded(MIN_TAP_MILLISECONDS.toDouble(), MAX_DOUBLE_TAP_PRESS_MILLISECONDS.toDouble()) {
            exp(person.tapLogMedian + random.gaussian() * person.tapLogSigma) * DOUBLE_TAP_PRESS_SCALE
        }.roundToInt().toLong()

    /**
     * Draws until the value falls inside the bounds. Clamping would pile draws on
     * the exact bound, and a repeated exact value is itself a scripted-input sign.
     */
    private inline fun bounded(minimum: Double, maximum: Double, draw: () -> Double): Double {
        repeat(MAX_REDRAWS) {
            val value = draw()
            if (value in minimum..maximum) return value
        }
        return draw().coerceIn(minimum, maximum)
    }

    private fun jitter(milliseconds: Long, logSigma: Double): Long =
        (milliseconds * exp(random.gaussian() * logSigma)).roundToInt().toLong()
            .coerceIn(DIKCIZ_ACCESSIBILITY_MINIMUM_GESTURE_DURATION_MILLISECONDS, DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_DURATION_MILLISECONDS)

    /** Where the finger lands: scattered inside the target, biased below its centre. */
    private fun landing(target: DikcizTouchTarget): Pair<Float, Float> {
        val width = target.width
        val height = target.height
        if (width == null || height == null) {
            val spread = UNSIZED_LANDING_SPREAD_MM * pixelsPerMillimetre
            return target.x + random.gaussian().toFloat() * spread to
                target.y + random.gaussian().toFloat() * spread
        }
        val x = target.x + random.gaussian().toFloat() * width * LANDING_SPREAD_SHARE
        val y = target.y + height * LANDING_DOWN_BIAS_SHARE + random.gaussian().toFloat() * height * LANDING_SPREAD_SHARE
        return clampToTarget(x, y, target)
    }

    private fun clampToTarget(x: Float, y: Float, target: DikcizTouchTarget): Pair<Float, Float> {
        val width = target.width ?: return x to y
        val height = target.height ?: return x to y
        val halfWidth = width * LANDING_INSET_SHARE
        val halfHeight = height * LANDING_INSET_SHARE
        return x.coerceIn(target.x - halfWidth, target.x + halfWidth) to
            y.coerceIn(target.y - halfHeight, target.y + halfHeight)
    }

    /** A press: the finger lands, may roll under a millimetre, and lifts after `press` ms. */
    private fun pressSamples(landing: Pair<Float, Float>, press: Long, driftShare: Double): List<DikcizTouchSample> {
        val (x, y) = landing
        if (random.nextDouble() >= driftShare || press < MIN_DRIFT_PRESS_MILLISECONDS) {
            return listOf(DikcizTouchSample(0, x, y), DikcizTouchSample(press, x, y))
        }
        val driftSigma = DikcizTouchPerson.withinPerson(TAP_DRIFT_LOG_SIGMA, DikcizTouchPerson.PERSON_DRIFT_LOG_SPREAD)
        val distance = bounded(0.0, MAX_TAP_DRIFT_MM) {
            exp(ln(TAP_DRIFT_MEDIAN_MM) + random.gaussian() * driftSigma) * person.driftScale
        } * pixelsPerMillimetre
        val angle = random.nextDouble() * 2 * PI
        val endX = x + (cos(angle) * distance).toFloat()
        val endY = y + (sin(angle) * distance).toFloat()
        // The roll happens while the finger settles, not at constant speed for the whole press.
        val settle = (press * random.nextDouble(SETTLE_MIN_SHARE, SETTLE_MAX_SHARE)).roundToInt().toLong().coerceAtLeast(1)
        return listOf(DikcizTouchSample(0, x, y), DikcizTouchSample(settle, endX, endY), DikcizTouchSample(press, endX, endY))
    }

    /**
     * A swipe path: a curve that bows sideways by about 5 % of its length, sub-pixel
     * tremor, and arc-length progress whose speed peaks early, as Touchalytics
     * swipes do. Samples are SAMPLE_INTERVAL_MILLISECONDS apart.
     */
    private fun path(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): List<DikcizTouchSample> {
        val dx = (endX - startX).toDouble()
        val dy = (endY - startY).toDouble()
        val length = hypot(dx, dy)
        val bow = drawDeviationShare() * length * (if (random.nextBoolean()) 1 else -1)
        val normalX = if (length == 0.0) 0.0 else -dy / length
        val normalY = if (length == 0.0) 0.0 else dx / length
        // Where along the path the bow is deepest; people curve more at one end.
        val apex = random.nextDouble(APEX_MIN, APEX_MAX)
        val peak = drawPeakFraction()
        val waves = List(SPEED_WAVES) {
            DikcizSpeedWave(random.nextDouble(MIN_WAVE_CYCLES, MAX_WAVE_CYCLES), random.nextDouble() * 2 * PI, random.gaussian())
        }
        // Touchalytics: start speed ratio median 0.66 (quartiles 0.21 and 1.37), end ratio
        // median 0.26 (0.06 and 0.71). The end target is set higher, at 0.4, because the
        // speed waves pull the measured end ratio down.
        val startRatio = bounded(MIN_START_RATIO, MAX_START_RATIO) { exp(ln(START_RATIO_MEDIAN) + random.gaussian() * START_RATIO_LOG_SIGMA) }
        val endRatio = bounded(MIN_END_RATIO, MAX_END_RATIO) { exp(ln(END_RATIO_TARGET) + random.gaussian() * END_RATIO_LOG_SIGMA) }
        val progress = progressCurve(peak, startRatio, endRatio, waves)
        val endJitter = END_SPREAD_MM * pixelsPerMillimetre
        val finalX = endX + random.gaussian() * endJitter
        val finalY = endY + random.gaussian() * endJitter
        val steps = max(MIN_PATH_SAMPLES, (duration / SAMPLE_INTERVAL_MILLISECONDS).toInt())
        val samples = ArrayList<DikcizTouchSample>(steps + 1)
        var tremorX = 0.0
        var tremorY = 0.0
        for (step in 0..steps) {
            val t = step.toDouble() / steps
            val s = progress(t)
            val baseX = startX + (finalX - startX) * s
            val baseY = startY + (finalY - startY) * s
            val offset = bow * bowShape(s, apex)
            if (step in 1 until steps) {
                // Tremor is a slow random walk, pulled back so it never builds up.
                tremorX = tremorX * TREMOR_DECAY + random.gaussian() * TREMOR_STEP_PX
                tremorY = tremorY * TREMOR_DECAY + random.gaussian() * TREMOR_STEP_PX
            } else {
                tremorX = 0.0
                tremorY = 0.0
            }
            val time = (duration * t).roundToInt().toLong()
            samples.add(
                DikcizTouchSample(
                    time,
                    (baseX + normalX * offset + tremorX).toFloat(),
                    (baseY + normalY * offset + tremorY).toFloat(),
                ),
            )
        }
        return samples
    }

    private fun drawDeviationShare(): Double {
        // Touchalytics: median 0.052 of the chord, p90 0.123; a lognormal through both,
        // with the person's own curvature taking part of that spread.
        val sigma = DikcizTouchPerson.withinPerson(ln(DEVIATION_P90 / DEVIATION_MEDIAN) / Z90, DikcizTouchPerson.PERSON_CURVATURE_LOG_SPREAD)
        return bounded(0.0, MAX_DEVIATION) {
            exp(ln(DEVIATION_MEDIAN) + random.gaussian() * sigma) * person.curvatureScale
        }
    }

    private fun drawPeakFraction(): Double {
        // Touchalytics: speed peaks at a median 0.355 of the stroke, p10 0.125, p90 0.788.
        // Each side is a half-normal through its percentile, less the person's own shift.
        val lower = DikcizTouchPerson.withinPerson((PEAK_MEDIAN - PEAK_P10) / Z90, DikcizTouchPerson.PERSON_PEAK_SPREAD)
        val upper = DikcizTouchPerson.withinPerson((PEAK_P90 - PEAK_MEDIAN) / Z90, DikcizTouchPerson.PERSON_PEAK_SPREAD)
        return bounded(MIN_PEAK, MAX_PEAK) {
            val side = if (random.nextBoolean()) -lower else upper
            PEAK_MEDIAN + abs(random.gaussian()) * side + person.peakShift
        }
    }

    companion object {
        // Touchalytics tap press time: lognormal mu 4.511 (89 ms median), sigma 0.808.
        const val TAP_LOG_MU = 4.511
        const val TAP_LOG_SIGMA = 0.808
        // Touchalytics: 35 % of taps roll, median 4 px and p90 9.4 px on its 233 to
        // 252 dpi phones, taken as 245 dpi: 0.41 mm and 0.98 mm. A press counts as a tap
        // up to 12 px (1.24 mm) of roll.
        const val TAP_DRIFT_SHARE = 0.35
        const val TAP_DRIFT_MEDIAN_MM = 0.41
        const val TAP_DRIFT_LOG_SIGMA = 0.68
        const val MAX_TAP_DRIFT_MM = 1.24
        // Not from data: no public double-tap timing set was usable. 110 ms sits inside
        // Android's double-tap window (40 to 300 ms between the first lift and second press).
        const val DOUBLE_TAP_GAP_MEDIAN_MILLISECONDS = 110.0
        const val DOUBLE_TAP_GAP_LOG_SIGMA = 0.25
        const val MIN_DOUBLE_TAP_GAP_MILLISECONDS = 50L
        const val MAX_DOUBLE_TAP_GAP_MILLISECONDS = 260L
        const val DOUBLE_TAP_PRESS_SCALE = 0.8
        // Touchalytics taps: p5 26 ms, p95 369 ms. Shorter presses read as synthetic, and
        // 400 ms or longer becomes Android's long press, so draws stay within these.
        const val MIN_TAP_MILLISECONDS = 25L
        const val MAX_TAP_MILLISECONDS = 380L
        const val MAX_DOUBLE_TAP_PRESS_MILLISECONDS = 140L
        const val DEFAULT_HOLD_MILLISECONDS = 650L
        // Touchalytics swipe duration median: 518 ms.
        const val DEFAULT_SWIPE_MILLISECONDS = 520L
        const val DEFAULT_TWO_FINGER_MILLISECONDS = 520L
        const val MIN_SWIPE_MILLISECONDS = 40L
        const val PRESS_JITTER_LOG_SIGMA = 0.12
        const val SWIPE_DURATION_JITTER_LOG_SIGMA = 0.12
        const val MAX_SECOND_FINGER_LAG_MILLISECONDS = 30L
        const val SAMPLE_INTERVAL_MILLISECONDS = 8L
        const val MIN_PATH_SAMPLES = 6
        const val DEVIATION_MEDIAN = 0.052
        const val DEVIATION_P90 = 0.123
        const val MAX_DEVIATION = 0.2
        const val PEAK_MEDIAN = 0.355
        const val PEAK_P10 = 0.125
        const val PEAK_P90 = 0.788
        const val MIN_PEAK = 0.08
        const val MAX_PEAK = 0.85

        private const val ALWAYS_DRIFT = 1.0
        private const val MIN_DRIFT_PRESS_MILLISECONDS = 20L
        private const val SETTLE_MIN_SHARE = 0.25
        private const val SETTLE_MAX_SHARE = 0.6
        // About 2 mm of spread when the target size is unknown; FFitts law (Bi, Li and
        // Zhai 2013) puts the finger's absolute precision near that.
        private const val UNSIZED_LANDING_SPREAD_MM = 0.6f
        private const val LANDING_SPREAD_SHARE = 0.12f
        private const val LANDING_DOWN_BIAS_SHARE = 0.06f
        private const val LANDING_INSET_SHARE = 0.35f
        private const val SECOND_TAP_SPREAD_MM = 0.35f
        private const val END_SPREAD_MM = 0.3
        private const val APEX_MIN = 0.35
        private const val APEX_MAX = 0.65
        private const val TREMOR_DECAY = 0.8
        private const val TREMOR_STEP_PX = 0.25
        private const val Z90 = 1.2816
        private const val SPEED_WAVES = 3
        private const val MIN_WAVE_CYCLES = 1.5
        private const val MAX_WAVE_CYCLES = 4.5
        private const val START_RATIO_MEDIAN = 0.66
        private const val START_RATIO_LOG_SIGMA = 1.38
        private const val MIN_START_RATIO = 0.01
        private const val MAX_START_RATIO = 4.0
        private const val END_RATIO_TARGET = 0.4
        private const val END_RATIO_LOG_SIGMA = 1.85
        private const val MIN_END_RATIO = 0.005
        private const val MAX_END_RATIO = 2.5
        private const val MAX_REDRAWS = 32

        /** Zero at both ends, one at `apex`: the sideways bow along the path. */
        internal fun bowShape(s: Double, apex: Double): Double = when {
            s <= 0.0 || s >= 1.0 -> 0.0
            s < apex -> sin(PI / 2 * s / apex)
            else -> sin(PI / 2 * (1 - s) / (1 - apex))
        }

        /**
         * Arc-length progress over normalized time. The speed is a Beta(a, b) bell with
         * its mode at `peak`, multiplied by a few slow `waves` (the small speed-ups and
         * slow-downs of a finger correcting its course), and trimmed at both ends so the
         * finger is already moving when it goes down and still moving when it lifts:
         * the mean speed over the first 15 % and last 15 % of the time, relative to the
         * middle 40 %, approaches `startRatio` and `endRatio`.
         */
        internal fun progressCurve(
            peak: Double,
            startRatio: Double = 0.0,
            endRatio: Double = 0.0,
            waves: List<DikcizSpeedWave> = emptyList(),
        ): (Double) -> Double {
            var from = 0.0
            var to = 1.0
            if (startRatio > 0.0 || endRatio > 0.0) {
                repeat(TRIM_ROUNDS) {
                    from = bisect(0.0, (to - MIN_TRIMMED_SPAN).coerceAtLeast(0.0)) { cut ->
                        endRatios(speedProfile(peak, cut, to, waves)).first < startRatio
                    }
                    to = 1.0 - bisect(0.0, 1.0 - from - MIN_TRIMMED_SPAN) { cut ->
                        endRatios(speedProfile(peak, from, 1.0 - cut, waves)).second < endRatio
                    }
                }
            }
            val speeds = speedProfile(peak, from, to, waves)
            val grid = DoubleArray(PROGRESS_GRID + 1)
            for (i in 0 until PROGRESS_GRID) grid[i + 1] = grid[i] + speeds[i]
            val total = grid[PROGRESS_GRID]
            return { t ->
                val position = t.coerceIn(0.0, 1.0) * PROGRESS_GRID
                val index = position.toInt().coerceAtMost(PROGRESS_GRID - 1)
                val fraction = position - index
                (grid[index] + (grid[index + 1] - grid[index]) * fraction) / total
            }
        }

        /** Speed over normalized time on the grid, for the bell between `from` and `to`. */
        private fun speedProfile(peak: Double, from: Double, to: Double, waves: List<DikcizSpeedWave>): DoubleArray {
            val mode = (from + peak * (to - from)).coerceIn(MIN_MODE, MAX_MODE)
            val a = 1 + PROGRESS_CONCENTRATION * mode
            val b = 1 + PROGRESS_CONCENTRATION * (1 - mode)
            val waveScale = if (waves.isEmpty()) 0.0 else WAVE_AMPLITUDE / sqrt(waves.size.toDouble())
            return DoubleArray(PROGRESS_GRID) { i ->
                val t = (i + 0.5) / PROGRESS_GRID
                val u = from + (to - from) * t
                val wave = waves.sumOf { it.weight * sin(2 * PI * it.cycles * t + it.phase) }
                Math.pow(u, a - 1) * Math.pow(1 - u, b - 1) * (1 + waveScale * wave).coerceAtLeast(MIN_WAVE_FACTOR)
            }
        }

        /** Mean speed over the first and last 15 % of the time, relative to the middle 40 %. */
        private fun endRatios(speeds: DoubleArray): Pair<Double, Double> {
            fun mean(from: Double, to: Double): Double {
                val first = (from * PROGRESS_GRID).toInt()
                val last = (to * PROGRESS_GRID).toInt()
                return (first until last).sumOf { speeds[it] } / (last - first)
            }
            val middle = mean(0.3, 0.7)
            return mean(0.0, 0.15) / middle to mean(0.85, 1.0) / middle
        }

        /** The largest value in [low, high] for which `below` still holds, by bisection. */
        private inline fun bisect(low: Double, high: Double, below: (Double) -> Boolean): Double {
            if (high <= low) return low
            var lower = low
            var upper = high
            repeat(BISECTION_STEPS) {
                val middle = (lower + upper) / 2
                if (below(middle)) lower = middle else upper = middle
            }
            return (lower + upper) / 2
        }

        // Fitted by simulation against Touchalytics swipes of 200 to 1000 ms and 100 px or
        // more, measured the way scripts/score-dikciz-touch.py measures them. With this bell
        // width, wave amplitude and the end targets below, the medians come out as start
        // speed ratio 0.55 (human 0.66), end ratio 0.20 (0.26), smoothed speed variation
        // about 0.65 before tremor (0.75) and speed peak at 0.40 of the time (0.355).
        private const val PROGRESS_CONCENTRATION = 20.0
        private const val WAVE_AMPLITUDE = 0.8
        private const val MIN_WAVE_FACTOR = 0.05
        private const val MIN_MODE = 0.02
        private const val MAX_MODE = 0.98
        private const val MIN_TRIMMED_SPAN = 0.2
        private const val TRIM_ROUNDS = 4
        private const val BISECTION_STEPS = 20
        private const val PROGRESS_GRID = 400
    }
}
