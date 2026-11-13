package com.emotionalleaves.studioagent.domain

/**
 * Twelve weeks of foundations for someone starting out, sized for weeknights: each week one skill, why it
 * matters on a gallery wall, three exercises of 20 to 30 minutes, and the Fresh eyes view that checks it.
 * Doing them badly is the point; finishing them is the whole goal.
 */
object Foundations {
    data class Exercise(val id: String, val text: String, val minutes: Int)
    data class Week(val n: Int, val skill: String, val why: String, val check: String, val exercises: List<Exercise>)

    private fun w(n: Int, skill: String, why: String, check: String, vararg ex: Pair<String, Int>) =
        Week(n, skill, why, check, ex.mapIndexed { i, (t, m) -> Exercise("$n${'a' + i}", t, m) })

    val weeks = listOf(
        w(1, "Seeing shapes", "Drawing what is there, not what you know is there, is the skill everything else rests on.", "Flipped",
            "Draw a chair by drawing only the spaces between its legs and rungs. 20 minutes." to 20,
            "Slow contour: draw your shoe without looking at the paper, the pen moving as slowly as your eye. Twice." to 20,
            "Draw a houseplant's outline as one continuous line, then again with your other hand." to 20),
        w(2, "Value: light and dark", "From across a room, people see light and dark long before color. Strong value reads as confident work.", "Three values",
            "A value scale: nine steps from white to black in pencil, each step evenly darker." to 20,
            "One egg or mug under a single lamp, drawn in only three tones: light, middle, dark." to 25,
            "Three thumbnails, phone-sized, of the same scene: one mostly light, one mostly middle, one mostly dark." to 25),
        w(3, "Edges", "Where an edge is sharp, the eye goes. Soft and lost edges make the sharp ones count.", "Squint",
            "Draw a crumpled paper bag. Make only three edges sharp; let every other edge go soft." to 25,
            "Paint or smudge a cloud of tone, then pull out one hard-edged shape from it with an eraser or a clean brush." to 20,
            "Draw a cup in front of a window; lose the edge where the cup and the background are the same value." to 25),
        w(4, "Proportion and measuring", "Things that are slightly off read as wrong, even to people who cannot say why.", "Flipped",
            "Measure a doorway with your pencil at arm's length: how many widths tall is it? Draw it to that ratio." to 20,
            "Draw a face from a photo with a light grid of thirds over both, square by square." to 30,
            "Sight-size: tape paper beside a small object and draw it the size you see it, checking by stepping back." to 30),
        w(5, "Composition", "Where you put things decides where people look and how long they stay.", "Thirds",
            "Six thumbnails of one scene, each with the main shape in a different place. Circle the best one." to 20,
            "Draw a still life of three objects where one is clearly the most important. Make it so with size, contrast or place." to 30,
            "Crop a photo you took four ways with paper L-shapes. Draw the strongest crop small." to 20),
        w(6, "Color: value and temperature", "A color is always lighter or darker, warmer or cooler than its neighbor. That is most of color.", "No color",
            "Paint or color six swatches that are all the same value but different colors. Check them with No color." to 25,
            "Paint an orange in only warm colors, then in only cool ones. Notice which feels more real." to 25,
            "Pick five colors in your room and line them up from lightest to darkest. Check with No color." to 20),
        w(7, "Mixing with a small palette", "A small palette makes a picture hold together; galleries see that as a voice.", "As it is",
            "With only three colors and white, mix ten different greens. Label each with what you mixed." to 25,
            "Paint a small landscape from a photo with only two colors and white." to 30,
            "Match the color of three things on your kitchen counter exactly, on paper beside them." to 25),
        w(8, "Light and form", "Light on form is what makes a flat surface look like a thing in a place.", "Three values",
            "Draw a ball under one lamp: light, the turning edge, the shadow side, the cast shadow." to 25,
            "Draw a box and a cylinder in the same light, with their cast shadows touching." to 25,
            "Draw a fold of fabric hanging from a hook in side light." to 30),
        w(9, "Marks and texture", "Your marks are your handwriting; they are what makes work recognizably yours.", "Squint",
            "Fill a page with twenty different marks from one tool. Star the three that feel most like you." to 20,
            "Draw tree bark, a towel and a metal spoon using marks only, no outlines." to 30,
            "Copy a small part of a painting you love, matching the marks rather than the picture." to 25),
        w(10, "From life and from photos", "Photos flatten light and squash space; knowing that lets you use them well.", "Two values",
            "Draw the same mug from life, then from a photo of it. Compare the shadows." to 30,
            "Take a photo with the flash off in window light and draw from it, simplifying the darks." to 25,
            "Draw a view out of a window in 10 minutes, then the same view from a photo in 10 minutes." to 20),
        w(11, "Simplify", "Big shapes first, details last. Most work is better with less in it.", "Squint",
            "Squint at a busy scene and draw it with no more than seven shapes." to 20,
            "Paint a portrait or a pet from a photo in under 30 minutes, with a brush too big for detail." to 30,
            "Take an old drawing and redraw it with half as many lines." to 20),
        w(12, "A small series of three", "Three pieces that belong together are the start of a body of work, and of a show.", "Flipped",
            "Pick one subject near you and plan three views of it: near, far, and a detail." to 20,
            "Make the first and second of the three, the same size and the same materials." to 30,
            "Make the third, lay all three side by side, and use Fresh eyes on each." to 30)
    )

    fun done(stored: String): Set<String> = stored.split(",").filter { it.isNotBlank() }.toSet()

    /** The week you are on: the first with an exercise still to do, or null when all twelve are done. */
    fun current(stored: String): Week? { val d = done(stored); return weeks.firstOrNull { wk -> wk.exercises.any { it.id !in d } } }

    fun next(stored: String): Exercise? { val d = done(stored); return current(stored)?.exercises?.firstOrNull { it.id !in d } }

    fun mark(stored: String, id: String, on: Boolean = true): String = (if (on) done(stored) + id else done(stored) - id).sortedWith(compareBy({ it.dropLast(1).toInt() }, { it.last() })).joinToString(",")

    fun byId(id: String): Exercise? = weeks.flatMap { it.exercises }.firstOrNull { it.id == id }
}
