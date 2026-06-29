package com.drakosanctis.auriga.hazard

/**
 * Auriga Hazard Taxonomy
 *
 * Ported from auriga-hazard-engine/taxonomy.ts (TypeScript) -> Kotlin.
 *
 * Defines the full hazard class hierarchy used by the scoring engine.
 */

enum class HazardCategory {
    STATIC, DYNAMIC, STRUCTURAL, UNKNOWN
}

data class HazardClassDef(
    val name: String,
    val label: String,
    val description: String,
    val defaultSeverityBase: Float,
    val isMobile: Boolean,
    val requiresImmediateAttention: Boolean,
    val notes: String? = null
)

object HazardTaxonomy {

    // STATIC
    val WALL = HazardClassDef("wall", "Wall", "Vertical solid barrier", 0.6f, false, false)
    val POLE = HazardClassDef("pole", "Pole / Post", "Vertical narrow obstacle (lamp post, signpost)", 0.55f, false, false)
    val FURNITURE = HazardClassDef("furniture", "Furniture", "Chairs, tables, benches, and other interior furnishings", 0.5f, false, false)
    val DOOR = HazardClassDef("door", "Door", "Door in any state (open, closed, ajar)", 0.4f, false, false, "May be moving; treat as DYNAMIC if in motion")
    val CONSTRUCTION_BARRIER = HazardClassDef("construction_barrier", "Construction Barrier", "Temporary construction or safety barriers", 0.65f, false, false)
    val HANGING_OBSTACLE = HazardClassDef("hanging_obstacle", "Hanging Obstacle", "Objects suspended at head height (low beams, signage, branches)", 0.75f, false, true, "High severity — difficult to detect and highly injurious")

    // DYNAMIC
    val PEDESTRIAN = HazardClassDef("pedestrian", "Pedestrian", "Moving person in the environment", 0.65f, true, false)
    val BICYCLE = HazardClassDef("bicycle", "Bicycle / Cyclist", "Bicycles and cyclists", 0.75f, true, true, "High speed potential; unpredictable trajectories")
    val VEHICLE = HazardClassDef("vehicle", "Vehicle", "Motor vehicles including cars, scooters, wheelchairs", 0.85f, true, true)
    val MOVING_OBJECT = HazardClassDef("moving_object", "Moving Object", "Other moving objects (carts, luggage, animals)", 0.6f, true, false)

    // STRUCTURAL
    val STAIRCASE = HazardClassDef("staircase", "Staircase", "Stairs going up or down", 0.85f, false, true)
    val RAMP = HazardClassDef("ramp", "Ramp", "Inclined surface — may be traversable or hazardous", 0.5f, false, false)
    val DROP_OFF = HazardClassDef("drop_off", "Drop-off / Ledge", "Sudden elevation drop (curb, platform edge, cliff)", 0.95f, false, true, "Maximum severity — fall risk is critical")
    val UNEVEN_GROUND = HazardClassDef("uneven_ground", "Uneven Ground", "Irregular surface, cobblestones, damaged flooring", 0.55f, false, false)

    // UNKNOWN
    val UNKNOWN_OBJECT = HazardClassDef("unknown_object", "Unknown Object", "Unclassified obstacle; treat as possible hazard", 0.6f, false, false, "Never silently discard — always surface with UNKNOWN status")
    val POSSIBLE_HAZARD = HazardClassDef("possible_hazard", "Possible Hazard", "Low-confidence detection requiring verification", 0.5f, false, false, "Constitutional constraint: never suppress possible hazards")

    private val byName: Map<String, Pair<HazardCategory, HazardClassDef>> = buildMap {
        put(WALL.name, HazardCategory.STATIC to WALL)
        put(POLE.name, HazardCategory.STATIC to POLE)
        put(FURNITURE.name, HazardCategory.STATIC to FURNITURE)
        put(DOOR.name, HazardCategory.STATIC to DOOR)
        put(CONSTRUCTION_BARRIER.name, HazardCategory.STATIC to CONSTRUCTION_BARRIER)
        put(HANGING_OBSTACLE.name, HazardCategory.STATIC to HANGING_OBSTACLE)

        put(PEDESTRIAN.name, HazardCategory.DYNAMIC to PEDESTRIAN)
        put(BICYCLE.name, HazardCategory.DYNAMIC to BICYCLE)
        put(VEHICLE.name, HazardCategory.DYNAMIC to VEHICLE)
        put(MOVING_OBJECT.name, HazardCategory.DYNAMIC to MOVING_OBJECT)

        put(STAIRCASE.name, HazardCategory.STRUCTURAL to STAIRCASE)
        put(RAMP.name, HazardCategory.STRUCTURAL to RAMP)
        put(DROP_OFF.name, HazardCategory.STRUCTURAL to DROP_OFF)
        put(UNEVEN_GROUND.name, HazardCategory.STRUCTURAL to UNEVEN_GROUND)

        put(UNKNOWN_OBJECT.name, HazardCategory.UNKNOWN to UNKNOWN_OBJECT)
        put(POSSIBLE_HAZARD.name, HazardCategory.UNKNOWN to POSSIBLE_HAZARD)
    }

    fun getCategoryForClass(hazardClass: String): HazardCategory =
        byName[hazardClass]?.first ?: HazardCategory.UNKNOWN

    fun getClassDef(hazardClass: String): HazardClassDef? =
        byName[hazardClass]?.second

    fun getDefaultSeverityBase(hazardClass: String): Float =
        getClassDef(hazardClass)?.defaultSeverityBase ?: 0.6f
}
