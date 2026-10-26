package com.techyfiposmeds.data.clinical

enum class AlertSeverity {
    HIGH_RISK,
    MODERATE,
    DUPLICATE_THERAPY
}

data class ClinicalInteractionAlert(
    val id: String,
    val severity: AlertSeverity,
    val titleEn: String,
    val titleBn: String,
    val medicineA: String,
    val genericA: String,
    val medicineB: String,
    val genericB: String,
    val mechanismEn: String,
    val mechanismBn: String,
    val recommendationEn: String,
    val recommendationBn: String
)

data class EvaluatedMedicine(
    val id: String,
    val name: String,
    val genericName: String?,
    val category: String? = null
)

object ClinicalDdiEngine {

    private val SALT_SUFFIX_REGEX = Regex(
        "\\b(sodium|potassium|hydrochloride|hcl|trihydrate|monohydrate|tartrate|succinate|besylate|mesylate|maleate|fumarate|calcium|phosphate|nitrate|sulphate|sulfate|acetate|hydrate|dipivoxil|propionate|valerate|hemihydrate|dihydrate)\\b",
        RegexOption.IGNORE_CASE
    )

    private val STRENGTH_REGEX = Regex(
        "\\b\\d+(\\.\\d+)?\\s*(mg|mcg|g|ml|iu|%|gm)\\b",
        RegexOption.IGNORE_CASE
    )

    fun normalizeGeneric(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var cleaned = raw.lowercase()
        cleaned = STRENGTH_REGEX.replace(cleaned, "")
        cleaned = SALT_SUFFIX_REGEX.replace(cleaned, "")
        return cleaned.replace(Regex("[^a-z0-9]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private val FLUOROQUINOLONES = setOf(
        "ciprofloxacin", "levofloxacin", "moxifloxacin", "ofloxacin", "gemifloxacin", "norfloxacin"
    )

    private val NSAIDS = setOf(
        "ketorolac", "naproxen", "diclofenac", "ibuprofen", "indomethacin", "aceclofenac",
        "mefenamic acid", "etoricoxib", "celecoxib", "meloxicam", "piroxicam", "aspirin", "acetylsalicylic acid"
    )

    private val ACE_ARBS = setOf(
        "enalapril", "ramipril", "lisinopril", "perindopril", "captopril",
        "losartan", "telmisartan", "valsartan", "olmesartan", "candesartan", "irbesartan"
    )

    private val POTASSIUM_SPARING = setOf(
        "spironolactone", "eplerenone", "amiloride", "triamterene", "potassium chloride", "potassium citrate"
    )

    private val ANTICOAGULANTS_ANTIPLATELETS = setOf(
        "warfarin", "rivaroxaban", "apixaban", "dabigatran", "heparin", "enoxaparin", "clopidogrel", "ticagrelor", "prasugrel"
    )

    private val PPI_CYP2C19_INHIBITORS = setOf(
        "omeprazole", "esomeprazole"
    )

    private val PDE5_INHIBITORS = setOf(
        "sildenafil", "tadalafil", "vardenafil", "avanafil"
    )

    private val NITRATES = setOf(
        "nitroglycerin", "glyceryl trinitrate", "isosorbide dinitrate", "isosorbide mononitrate", "nicorandil"
    )

    private val STATINS = setOf(
        "atorvastatin", "simvastatin", "lovastatin", "rosuvastatin", "pravastatin"
    )

    private val MACROLIDES_CYP3A4 = setOf(
        "clarithromycin", "erythromycin"
    )

    private val SSRI_SNRI = setOf(
        "escitalopram", "citalopram", "fluoxetine", "sertraline", "paroxetine", "fluvoxamine", "duloxetine", "venlafaxine"
    )

    fun evaluateCart(medicines: List<EvaluatedMedicine>): List<ClinicalInteractionAlert> {
        if (medicines.size < 2) return emptyList()

        val alerts = mutableListOf<ClinicalInteractionAlert>()
        val seenPairs = mutableSetOf<String>()

        for (i in 0 until medicines.size) {
            for (j in i + 1 until medicines.size) {
                val medA = medicines[i]
                val medB = medicines[j]
                val genA = normalizeGeneric(medA.genericName)
                val genB = normalizeGeneric(medB.genericName)

                val pairKey = if (medA.name <= medB.name) "${medA.name}_${medB.name}" else "${medB.name}_${medA.name}"
                if (seenPairs.contains(pairKey)) continue

                // 1. Duplicate Generic Therapy Check
                if (genA.isNotBlank() && genB.isNotBlank() && (genA == genB || isDuplicateTherapy(genA, genB))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DUP_${medA.id}_${medB.id}",
                            severity = AlertSeverity.DUPLICATE_THERAPY,
                            titleEn = "Duplicate Active Ingredient Therapy",
                            titleBn = "একই উপাদানের দ্বৈত ওষুধ নির্বাচন",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Both medications contain the same active therapeutic agent (${medA.genericName ?: genA}). Simultaneous administration may result in accidental double dosage, heightened adverse effects, or organ toxicity.",
                            mechanismBn = "উভয় ওষুধেই একই সক্রিয় উপাদান (${medA.genericName ?: genA}) রয়েছে। একসাথে গ্রহণে অতিরিক্ত মাত্রার পার্শ্বপ্রতিক্রিয়া বা টক্সিসিটির ঝুঁকি তৈরি হয়।",
                            recommendationEn = "Verify whether duplicate therapy was intentional. Consider keeping only one brand or consulting the prescriber.",
                            recommendationBn = "প্রেসক্রিপশন পরীক্ষা করুন এবং অপ্রয়োজনীয় হলে একটি ব্র্যান্ড বাদ দিয়ে রোগীর অতিরিক্ত ওভারডোজ রোধ করুন।"
                        )
                    )
                    continue
                }

                // 2. Fluoroquinolone + NSAID
                if ((matchesAny(genA, FLUOROQUINOLONES) && matchesAny(genB, NSAIDS)) ||
                    (matchesAny(genB, FLUOROQUINOLONES) && matchesAny(genA, NSAIDS))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_FQ_NSAID_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Fluoroquinolone + NSAID High Risk Interaction",
                            titleBn = "ফ্লুরোকুইনোলোন ও ব্যথানাশক তীব্র মিথস্ক্রিয়া",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Co-administration of fluoroquinolone antibiotics with non-steroidal anti-inflammatory drugs (NSAIDs) markedly increases CNS stimulation and the risk of epileptic seizures and convulsions.",
                            mechanismBn = "সিপ্রোফ্লক্সাসিন/লেভোফ্লক্সাসিনের সাথে ব্যথানাশক (NSAIDs) একসাথে ব্যবহারে কেন্দ্রীয় স্নায়ুতন্ত্র উত্তেজিত হয়ে খিঁচুনি বা সিজার হওয়ার তীব্র ঝুঁকি থাকে।",
                            recommendationEn = "Substitute the NSAID with Paracetamol for analgesia, or prescribe an alternative antibiotic class if analgesia is essential.",
                            recommendationBn = "ব্যথা নিরাময়ে NSAID এর পরিবর্তে প্যারাসিটামল ব্যবহার করুন অথবা চিকিৎসকের সাথে পরামর্শ করে অ্যান্টিবায়োটিক পরিবর্তন করুন।"
                        )
                    )
                    continue
                }

                // 3. ACE-I / ARB + Potassium Sparing Diuretic
                if ((matchesAny(genA, ACE_ARBS) && matchesAny(genB, POTASSIUM_SPARING)) ||
                    (matchesAny(genB, ACE_ARBS) && matchesAny(genA, POTASSIUM_SPARING))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_ACE_K_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Severe Hyperkalemia Risk (ACE-I/ARB + K-Sparing)",
                            titleBn = "মারাত্মক রক্তে পটাশিয়াম বৃদ্ধি ঝুঁকি (হাইপারক্যালেমিয়া)",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Both agent classes reduce aldosterone-mediated potassium excretion, creating a potent synergistic risk of severe, life-threatening hyperkalemia and fatal cardiac arrhythmias.",
                            mechanismBn = "উভয় ওষুধই শরীর থেকে পটাশিয়াম নিঃসরণ কমিয়ে দেয়, যার ফলে রক্তে পটাশিয়াম মারাত্মক বেড়ে গিয়ে হার্ট অ্যাটাক বা বিপজ্জনক হৃদস্পন্দন সমস্যা হতে পারে।",
                            recommendationEn = "Strictly monitor serum potassium levels, renal function, or adjust diuretic therapy under medical supervision.",
                            recommendationBn = "রোগীর রক্তের পটাশিয়াম ও কিডনির কার্যকারিতা পরীক্ষা নিশ্চিত করুন এবং অতিরিক্ত পটাশিয়াম গ্রহণ থেকে বিরত রাখুন।"
                        )
                    )
                    continue
                }

                // 4. Clopidogrel + Omeprazole / Esomeprazole
                if ((matchesExact(genA, "clopidogrel") && matchesAny(genB, PPI_CYP2C19_INHIBITORS)) ||
                    (matchesExact(genB, "clopidogrel") && matchesAny(genA, PPI_CYP2C19_INHIBITORS))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_CLOP_PPI_${medA.id}_${medB.id}",
                            severity = AlertSeverity.MODERATE,
                            titleEn = "Clopidogrel Efficacy Reduction via CYP2C19",
                            titleBn = "ক্লোপিডোগ্রেলের কার্যকারিতা হ্রাস (ওমিপ্রাজল/ইসোমিপ্রাজল)",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Omeprazole and Esomeprazole inhibit hepatic CYP2C19 enzymes, preventing bioactivation of clopidogrel into its active antiplatelet form, reducing cardiovascular protection.",
                            mechanismBn = "ওমিপ্রাজল বা ইসোমিপ্রাজল সেবনে লিভারে ক্লোপিডোগ্রেলের সক্রিয় রূপান্তর বাধাগ্রস্ত হয়, ফলে হৃদরোগের চিকিৎসায় রক্ত তরল রাখার সুরক্ষা উল্লেখযোগ্যভাবে কমে যায়।",
                            recommendationEn = "Recommend switching gastric protection to Pantoprazole or Rabeprazole, which exhibit significantly weaker CYP2C19 inhibition.",
                            recommendationBn = "ওমিপ্রাজল বা ইসোমিপ্রাজলের বদলে প্যান্টোপ্রাজল বা রাবেপ্রাজল গ্রহণের পরামর্শ দিন, যা ক্লোপিডোগ্রেলকে বাধাগ্রস্ত করে না।"
                        )
                    )
                    continue
                }

                // 5. Anticoagulant / Antiplatelet + NSAIDs
                if ((matchesAny(genA, ANTICOAGULANTS_ANTIPLATELETS) && matchesAny(genB, NSAIDS)) ||
                    (matchesAny(genB, ANTICOAGULANTS_ANTIPLATELETS) && matchesAny(genA, NSAIDS))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_AC_NSAID_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Major Hemorrhage / GI Bleeding Risk",
                            titleBn = "মারাত্মক গ্যাস্ট্রিক ও অভ্যন্তরীণ রক্তক্ষরণের ঝুঁকি",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Combined use of systemic blood thinners/anticoagulants with NSAIDs causes additive platelet inhibition and gastrointestinal mucosal damage, drastically elevating major bleeding risk.",
                            mechanismBn = "রক্ত পাতলা রাখার ওষুধের সাথে ব্যথানাশক (NSAIDs) গ্রহণে পাকস্থলীর ঝিল্লি ক্ষতিগ্রস্ত হয়ে ভয়াবহ অভ্যন্তরীণ রক্তক্ষরণ ও আলসারের ঝুঁকি তৈরি হয়।",
                            recommendationEn = "Avoid systemic NSAIDs in anticoagulated patients. Use Paracetamol or topical preparations whenever feasible.",
                            recommendationBn = "রক্ত পাতলা রাখা রোগীদের ক্ষেত্রে মুখে খাওয়ার NSAID এড়িয়ে চলুন এবং নিরাপদ বিকল্প হিসেবে প্যারাসিটামল ব্যবহার করুন।"
                        )
                    )
                    continue
                }

                // 6. PDE-5 Inhibitor + Nitrates
                if ((matchesAny(genA, PDE5_INHIBITORS) && matchesAny(genB, NITRATES)) ||
                    (matchesAny(genB, PDE5_INHIBITORS) && matchesAny(genA, NITRATES))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_PDE5_NIT_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Fatal Precipitous Hypotension Contraindication",
                            titleBn = "বিপজ্জনক রক্তচাপ পতন ও জীবনঘাতী ঝুঁকি",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Concurrent administration of PDE-5 inhibitors with nitrates causes synergistic cyclic GMP accumulation, producing profound, uncontrollable vasodilation and life-threatening shock.",
                            mechanismBn = "হৃদরোগের নাইট্রেট ওষুধের সাথে সিলডেনাফিল বা টাদালাফিল গ্রহণ সম্পূর্ণ নিষিদ্ধ। এতে রক্তনালী হঠাৎ অতিমাত্রায় প্রসারিত হয়ে রক্তচাপ শূন্যে নেমে মৃত্যু পর্যন্ত হতে পারে।",
                            recommendationEn = "Absolute contraindication. DO NOT dispense concurrently under any circumstance.",
                            recommendationBn = "এই সমন্বয় সম্পূর্ণ নিষিদ্ধ। কোনো অবস্থাতেই এই দুটি ওষুধ একসাথে রোগীকে প্রদান করবেন না।"
                        )
                    )
                    continue
                }

                // 7. Statins + Macrolides
                if ((matchesAny(genA, STATINS) && matchesAny(genB, MACROLIDES_CYP3A4)) ||
                    (matchesAny(genB, STATINS) && matchesAny(genA, MACROLIDES_CYP3A4))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_STATIN_MAC_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Severe Rhabdomyolysis & Myopathy Risk",
                            titleBn = "তীব্র পেশী ক্ষয় (র‍্যাবডোমায়োলাইসিস) ও কিডনি বিকল ঝুঁকি",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Clarithromycin and erythromycin strongly inhibit CYP3A4-mediated statin metabolism, elevating statin blood concentrations up to 10-fold and precipitating acute rhabdomyolysis.",
                            mechanismBn = "ক্ল্যারিথ্রোমাইসিন সেবনে কোলেস্টেরলের ওষুধের মেটাবলিজম বন্ধ হয়ে রক্তে স্ট্যাটিনের মাত্রা ১০ গুণ পর্যন্ত বৃদ্ধি পায় এবং মারাত্মক পেশী ক্ষয় ও কিডনি অকেজো হতে পারে।",
                            recommendationEn = "Withhold statin therapy for the duration of the macrolide course, or switch antibiotic to Azithromycin.",
                            recommendationBn = "অ্যান্টিবায়োটিক চলার সময় স্ট্যাটিন সাময়িক বন্ধ রাখুন অথবা চিকিৎসকের পরামর্শে অ্যাজিথ্রোমাইসিন গ্রহণ করুন।"
                        )
                    )
                    continue
                }

                // 8. SSRI / SNRI + Tramadol
                if ((matchesAny(genA, SSRI_SNRI) && matchesExact(genB, "tramadol")) ||
                    (matchesAny(genB, SSRI_SNRI) && matchesExact(genA, "tramadol"))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_SEROTONIN_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Serotonin Syndrome Danger",
                            titleBn = "সেরোটোনিন সিনড্রোমের মারাত্মক ঝুঁকি",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "Tramadol inhibits serotonin reuptake while SSRIs/SNRIs elevate central synaptic serotonin. Co-prescription risks Serotonin Syndrome, characterized by autonomic instability, rigidity, and hyperthermia.",
                            mechanismBn = "অ্যান্টিডিপ্রেসেন্টের সাথে ট্রামাডল গ্রহণে মস্তিষ্কে সেরোটোনিনের মাত্রা বিপজ্জনকভাবে বৃদ্ধি পায়, যা চরম কাঁপুনি, বিভ্রান্তি ও উচ্চ জ্বরের মতো সংকট তৈরি করতে পারে।",
                            recommendationEn = "Avoid concurrent use. Use non-serotonergic analgesics (Paracetamol, Naproxen) instead.",
                            recommendationBn = "একসাথে গ্রহণ এড়িয়ে চলুন এবং ট্রামাডলের বদলে প্যারাসিটামল বা অন্য নিরাপদ ব্যথানাশক ব্যবহার করুন।"
                        )
                    )
                    continue
                }

                // 9. Methotrexate + NSAIDs
                if ((matchesExact(genA, "methotrexate") && matchesAny(genB, NSAIDS)) ||
                    (matchesExact(genB, "methotrexate") && matchesAny(genA, NSAIDS))) {
                    seenPairs.add(pairKey)
                    alerts.add(
                        ClinicalInteractionAlert(
                            id = "DDI_MTX_NSAID_${medA.id}_${medB.id}",
                            severity = AlertSeverity.HIGH_RISK,
                            titleEn = "Severe Methotrexate Toxicity & Marrow Suppression",
                            titleBn = "মেথোট্রেক্সেট বিষক্রিয়া ও বোনম্যারো ক্ষতিগ্রস্ত ঝুঁকি",
                            medicineA = medA.name,
                            genericA = medA.genericName ?: genA,
                            medicineB = medB.name,
                            genericB = medB.genericName ?: genB,
                            mechanismEn = "NSAIDs reduce renal tubular clearance of methotrexate, leading to elevated toxic plasma concentrations, bone marrow suppression, and severe cytopenia.",
                            mechanismBn = "ব্যথানাশক (NSAIDs) কিডনি দিয়ে মেথোট্রেক্সেট বের হতে বাধা দেয়, ফলে রক্তে বিষক্রিয়ার মাত্রা বেড়ে গিয়ে অস্থিমজ্জা ক্ষতিগ্রস্ত হতে পারে।",
                            recommendationEn = "Do not dispense NSAIDs to patients taking methotrexate without specialized rheumatology oversight.",
                            recommendationBn = "মেথোট্রেক্সেট গ্রহণকারী রোগীকে চিকিৎসকের বিশেষ অনুমতি ছাড়া ব্যথানাশক প্রদান করবেন না।"
                        )
                    )
                    continue
                }
            }
        }

        return alerts
    }

    private fun isDuplicateTherapy(genA: String, genB: String): Boolean {
        if (genA.length > 4 && genB.length > 4) {
            if (genA.contains(genB) || genB.contains(genA)) return true
        }
        return false
    }

    private fun matchesAny(generic: String, set: Set<String>): Boolean {
        if (generic.isBlank()) return false
        if (set.contains(generic)) return true
        return set.any { key -> generic.contains(key) || key.contains(generic) }
    }

    private fun matchesExact(generic: String, target: String): Boolean {
        if (generic.isBlank()) return false
        return generic == target || generic.contains(target)
    }
}
