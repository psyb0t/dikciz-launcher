package org.fossify.home.dikciz

import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DikcizTouchModelTest {
    private fun model(seed: Int) = DikcizTouchModel(random = Random(seed), pixelsPerMillimetre = PIXELS_PER_MM)

    @Test
    fun theSameSeedPlansTheSameGesture() {
        val target = DikcizTouchTarget(500, 900, 300, 120)
        assertEquals(model(7).tap(target, null), model(7).tap(target, null))
        assertEquals(model(7).swipe(100, 1500, 120, 400, null), model(7).swipe(100, 1500, 120, 400, null))
    }

    @Test
    fun tapsVaryTheirPressAndLandInsideTheTargetBelowItsCentre() {
        val touch = model(11)
        val target = DikcizTouchTarget(500, 900, 300, 120)
        val plans = (1..SAMPLES).map { touch.tap(target, null) }
        val presses = plans.map { it.totalDurationMilliseconds }
        val landings = plans.map { it.pointers.single().samples.first() }
        val median = presses.sorted()[presses.size / 2]
        assertTrue("median press $median ms", median in 60L..130L)
        assertTrue(presses.toSet().size > SAMPLES / 10)
        assertTrue("presses stay between the bounds", presses.all { it in 25L..380L })
        assertTrue("draws are redrawn, not piled on a bound", presses.count { it == 25L || it == 380L } < 4)
        assertTrue(landings.all { it.x in 395f..605f && it.y in 858f..942f })
        assertTrue(landings.map { it.x to it.y }.toSet().size > SAMPLES * 9 / 10)
        assertTrue("taps land below centre", landings.map { it.y }.average() > 900)
        val rolled = plans.count { it.pointers.single().moves }
        assertTrue("$rolled of $SAMPLES taps roll", rolled in SAMPLES / 10..SAMPLES * 6 / 10)
    }

    @Test
    fun aRequestedDurationIsKeptCloseButNotExact() {
        val presses = (1..SAMPLES).map { model(it).hold(DikcizTouchTarget(300, 300, null, null), 800).totalDurationMilliseconds }
        assertTrue(presses.all { it in 500L..1200L })
        assertTrue(presses.toSet().size > 20)
    }

    @Test
    fun doubleTapsLeaveAHumanGapAndTheSecondPressFollowsTheFirst() {
        val touch = model(3)
        repeat(SAMPLES) {
            val (first, second) = touch.doubleTap(DikcizTouchTarget(400, 400, 200, 200), null).pointers
            val gap = second.startMilliseconds - first.endMilliseconds
            assertTrue("gap $gap", gap in 50L..260L)
            val distance = hypot(first.samples[0].x - second.samples[0].x, first.samples[0].y - second.samples[0].y)
            assertTrue("second press $distance px away", distance < 30f)
        }
    }

    @Test
    fun swipesCurveChangeSpeedAndEndNearTheirTarget() {
        val touch = model(5)
        val deviations = mutableListOf<Double>()
        val variations = mutableListOf<Double>()
        repeat(SAMPLES) {
            val samples = touch.swipe(540, 1600, 540, 600, 400).pointers.single().samples
            assertEquals(540f, samples.first().x)
            assertEquals(1600f, samples.first().y)
            assertTrue(hypot(samples.last().x - 540f, samples.last().y - 600f) < 25f)
            assertTrue(samples.zipWithNext().all { (a, b) -> b.timeMilliseconds > a.timeMilliseconds })
            deviations.add(samples.maxOf { kotlin.math.abs(it.x - 540f) } / 1000.0)
            val speeds = samples.zipWithNext().map { (a, b) ->
                hypot(b.x - a.x, b.y - a.y) / (b.timeMilliseconds - a.timeMilliseconds)
            }
            val mean = speeds.average()
            val spread = kotlin.math.sqrt(speeds.sumOf { (it - mean) * (it - mean) } / speeds.size)
            variations.add(spread / mean)
        }
        val medianVariation = variations.sorted()[variations.size / 2]
        assertTrue("median speed variation $medianVariation", medianVariation > 0.4)
        assertTrue("no swipe moves at one constant speed", variations.all { it > 0.15 })
        val median = deviations.sorted()[deviations.size / 2]
        assertTrue("median bow $median of the chord", median in 0.02..0.1)
    }

    @Test
    fun twoFingerSwipesStartAFewMillisecondsApart() {
        val touch = model(9)
        repeat(SAMPLES) {
            val (first, second) = touch.twoFingerSwipe(intArrayOf(300, 900, 450, 900), intArrayOf(700, 900, 550, 900), null).pointers
            assertEquals(0L, first.startMilliseconds)
            assertTrue(second.startMilliseconds in 0L..30L)
            assertTrue(first.moves && second.moves)
        }
    }

    @Test
    fun progressRisesFromZeroToOneAndPeaksWhereAsked() {
        listOf(0.15, 0.355, 0.7).forEach { peak ->
            val progress = DikcizTouchModel.progressCurve(peak)
            assertEquals(0.0, progress(0.0), 1e-9)
            assertEquals(1.0, progress(1.0), 1e-9)
            val steps = (0..100).map { progress(it / 100.0) }
            assertTrue(steps.zipWithNext().all { (a, b) -> b >= a })
            val fastest = steps.zipWithNext().withIndex().maxBy { (_, pair) -> pair.second - pair.first }.index / 100.0
            assertEquals(peak, fastest, 0.06)
        }
    }

    @Test
    fun trimmedProgressStartsAndEndsAtTheRequestedSpeed() {
        listOf(0.3 to 0.1, 0.66 to 0.26, 0.9 to 0.5).forEach { (start, end) ->
            val progress = DikcizTouchModel.progressCurve(0.355, start, end)
            fun speed(from: Double, to: Double) = (progress(to) - progress(from)) / (to - from)
            val middle = speed(0.3, 0.7)
            assertEquals("start ratio", start, speed(0.0, 0.15) / middle, start * 0.15)
            assertEquals("end ratio", end, speed(0.85, 1.0) / middle, end * 0.15)
            assertEquals(1.0, progress(1.0), 1e-9)
        }
    }

    private companion object {
        const val SAMPLES = 400
        const val PIXELS_PER_MM = 16.5f
    }
}
