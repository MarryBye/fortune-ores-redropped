# -*- coding: utf-8 -*-
"""Which silhouette, colour and material style each redrawn ore chunk gets.

    texture file name -> (form, base colour, style, accent colour or None)

A trailing "^" on the form name mirrors it horizontally, which is how two related
materials (aluminium foil flakes and magnesium ribbon, say) stay recognisably
different without a second hand-drawn silhouette.

Styles change the tone ramp and the lighting, not the shape:
    metal    opaque, hard specular, cool shadows
    gem      cut stone: high contrast, light carried into the lower half
    crystal  raw prisms: like gem, slightly softer
    rock     matte, low contrast
    organic  matte but warm
    glow     self-lit: shadows lifted, accent colour used for the bright specks

The chunks not listed here already read well and were left alone: the vanilla
four, the metals with hand-drawn textures (copper, iron, gold), and the gems and
crystals that were already faceted (diamond, emerald, ruby, sapphire, amethyst,
aquamarine, apatite, cinnabar, draconium, both certus quartzes, ...).
"""

ORES = {
    # ---- raw metal lumps and wrought shapes -----------------------------------
    "crimson_iron": ("nugget_round", "#C4485A", "metal", None),
    "tin": ("nugget_round^", "#BFCEDA", "metal", None),
    "lead": ("nugget_wide", "#6E6E8C", "metal", None),
    "atlarus": ("nugget_tall", "#E8C24A", "metal", None),
    "ceruclase": ("nugget_twin", "#7A93B8", "metal", None),
    "zinc": ("nugget_twin^", "#B6C8C6", "metal", None),
    "eximite": ("pebble_trio", "#2E8A7E", "metal", None),
    "aluminum": ("flake_stack", "#CBD4DA", "metal", None),
    "magnesium": ("flake_stack^", "#CFCFC4", "metal", None),
    "lutetium": ("billet_bar", "#E4E2C0", "metal", None),
    "carmot": ("crescent_horn", "#D9A05B", "metal", None),
    "cobalt": ("spike_cluster", "#3B6BD6", "metal", None),
    "osmium": ("hex_plate", "#9EC8DE", "metal", None),
    "midasium": ("molten_drip", "#E8A824", "metal", None),
    "shadow_iron": ("shard_plate", "#55556A", "metal", None),
    "deep_iron": ("ingot_rough", "#5A6A78", "metal", None),
    "promethium": ("claw_pair", "#4FA05A", "metal", None),
    "pyridium": ("bar_twisted", "#E07A20", "metal", None),
    "iridium": ("sheet_folded", "#D2DCE4", "metal", None),
    "desh": ("slag_chunk", "#8E8272", "metal", None),
    "infuscolium": ("blade_shard", "#8A3A88", "metal", None),
    "sanguinite": ("blade_shard^", "#A81020", "metal", None),
    "meutoite": ("dagger_point", "#6A5A8E", "metal", None),
    "oureclase": ("nugget_notch", "#2E8A78", "metal", None),
    "rubracium": ("scale_plate", "#B03A38", "metal", None),
    "titanium": ("layered_slab", "#A9B0B8", "metal", None),
    "tungsten": ("pyramid_step", "#4E5A52", "metal", None),
    "adamantine": ("arrow_shard", "#C22B2B", "metal", None),
    "orichalcum": ("crystal_rose", "#5FA07A", "metal", None),
    "pyrite": ("cube_crystal", "#D6B84A", "metal", None),
    "silver": ("needle_fan", "#E2ECF2", "metal", None),
    "cincinnasite": ("wedge_pair", "#96402E", "metal", None),

    # ---- rock, powder and salt ------------------------------------------------
    "mangnanese": ("matrix_lump", "#8A87A0", "rock", None),
    "nickel": ("granule_pile", "#C9C9A0", "rock", None),
    "potash": ("clod_lump", "#D8A05A", "rock", None),
    "lithium": ("powder_heap", "#DCDCE6", "rock", None),
    "saltpeter": ("powder_heap^", "#EEEADA", "rock", None),
    "boron": ("salt_crumb", "#6E6A5E", "rock", None),
    "biotite": ("sheet_folded^", "#4A3E30", "rock", None),
    "coal": ("coal_lump", "#2E2E32", "rock", None),
    "dark": ("chip_pair", "#3E3050", "rock", None),
    "rutile": ("bar_twisted^", "#C46A2A", "rock", None),
    "thorium": ("vein_slab", "#4A5A4A", "rock", "#6ECF5A"),
    "foulite": ("moss_lump", "#7EA82E", "rock", None),

    # ---- organic --------------------------------------------------------------
    "fossil": ("ammonite", "#A89070", "organic", None),
    "cheese": ("cheese_wedge", "#F0C02E", "organic", None),
    "lemurite": ("bone_pair", "#DCDCCC", "organic", None),
    "viroxores": ("bubble_cluster", "#7EC42E", "organic", None),

    # ---- crystals -------------------------------------------------------------
    "alduorite": ("crystal_cluster", "#86C2D2", "crystal", None),
    "mithril": ("crystal_cluster^", "#A9DCEE", "crystal", None),
    "kalendrite": ("prism_pair", "#9A54C4", "crystal", None),
    "astral_silver": ("twin_prism_tall", "#BCDCEC", "crystal", None),
    "linium": ("needle_fan^", "#A8C8E0", "crystal", None),
    "quartz": ("prism_single", "#EDE6DC", "crystal", None),
    "silicon": ("wafer_chip", "#6E7A86", "crystal", None),
    "dilithium": ("shard_cross", "#BFEDE4", "crystal", None),
    "rock_crystal": ("crown_spikes", "#E8F0F4", "crystal", None),
    "coralium": ("coral_branch", "#3E9E76", "crystal", None),

    # ---- cut gems: one cut per stone, so the shape names the gem --------------
    "zanite": ("cut_brilliant", "#7B4FB0", "gem", None),
    "benitoite": ("cut_emerald", "#2E6ACC", "gem", None),
    "peridot": ("cut_marquise", "#A5D63C", "gem", None),
    "topaz": ("cut_pear", "#E8A020", "gem", None),
    "tanzanite": ("cut_trillion", "#5A4FC4", "gem", None),
    "anglesite": ("cut_baguette", "#E6D69A", "gem", None),
    "neridium": ("cut_cushion", "#C43C9E", "gem", None),
    "onyx": ("cut_octagon", "#26262E", "gem", None),
    "amber": ("cut_teardrop_side", "#E8901A", "gem", None),
    "malachite": ("scale_plate^", "#23A05E", "gem", None),
    "coralium_pearl": ("orb_polished", "#1E7A5E", "gem", None),
    "pearlescent_coralium": ("orb_ringed", "#8EE0C8", "gem", None),

    # ---- self-lit -------------------------------------------------------------
    "starsteel": ("glow_rock", "#33334A", "glow", "#FF9A3C"),
    "ambrosium": ("glow_rock^", "#E8B84A", "glow", "#FFF3B4"),
    "ignatius": ("ember_lump", "#E8661C", "glow", "#FFD060"),
    "vulcanite": ("molten_drip^", "#D6531E", "glow", "#FFA050"),
    "uranium": ("geode", "#47A83A", "glow", "#C8FF7A"),
    "yellorium": ("cluster_dust", "#E8D53A", "glow", "#FFF7A0"),
    "resonating": ("star_burst", "#C42A2A", "glow", "#FF9A7A"),
    "solar": ("sun_disc", "#F0C020", "glow", "#FFF0A0"),
    "gravitite": ("winged_shard", "#C24FD6", "glow", "#F2C4FF"),
    "shadow": ("swirl_orb", "#4A3468", "glow", "#A87ADC"),
    "rune": ("rune_tablet", "#7A4FC4", "glow", "#C8A8FF"),
    "liquified_coralium": ("droplet", "#2ECFA8", "glow", "#B0FFE8"),

    # ---- Thaumcraft primals: one shape per aspect ------------------------------
    "infused_air": ("wisp_curl", "#FFF06A", "glow", "#FFFFC8"),
    "infused_fire": ("flame_shard", "#FF5A1E", "glow", "#FFC060"),
    "infused_water": ("ring_torus", "#3ED8E8", "glow", "#C0FFFF"),
    "infused_earth": ("boulder_split", "#3ECC3E", "glow", "#C0FFC0"),
    "infused_order": ("octa_symmetry", "#DCDCF5", "glow", "#FFFFFF"),
    "infused_entropy": ("fracture_shards", "#4E4E58", "glow", "#B4B4C4"),
}
