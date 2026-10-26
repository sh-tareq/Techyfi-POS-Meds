package com.techyfiposmeds.data.clinical

data class OtcRecommendation(
    val genericName: String,
    val dosageForm: String,
    val standardDosageEn: String,
    val standardDosageBn: String,
    val commonBrands: List<String>,
    val defaultQty: Double = 10.0,
    val isPrimary: Boolean = true,
    val clinicalTipEn: String,
    val clinicalTipBn: String
)

data class OtcCategory(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val subtitleEn: String,
    val subtitleBn: String,
    val redFlagsEn: List<String>,
    val redFlagsBn: List<String>,
    val recommendations: List<OtcRecommendation>
)

object OtcTriageEngine {

    val categories: List<OtcCategory> = listOf(
        OtcCategory(
            id = "gastric",
            titleEn = "Acidity & Heartburn",
            titleBn = "গ্যাস, বুকজ্বালা ও এসিডিটি",
            subtitleEn = "Acid reflux, gastritis, indigestion, bloating",
            subtitleBn = "গ্যাসের সমস্যা, পেটে জ্বালাপোড়া, পেট ফাঁপা ও বদহজম",
            redFlagsEn = listOf(
                "Severe persistent chest pain radiating to left arm/jaw (Rule out Myocardial Infarction / Heart Attack)",
                "Black tarry stool or vomiting blood (Active GI bleeding warning)",
                "Difficulty or severe pain while swallowing"
            ),
            redFlagsBn = listOf(
                "তীব্র বুকে ব্যথা যা বাম হাত বা চোয়ালে ছড়ায় (হার্ট অ্যাটাকের ঝুঁকি - জরুরি হাসপাতালে যান)",
                "কালো আলকাতরার মতো পায়খানা বা রক্তবমি (পাকস্থলীর রক্তক্ষরণ ঝুঁকি)",
                "খাবার গিলতে তীব্র কষ্ট বা ব্যথা হওয়া"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Esomeprazole 20mg",
                    dosageForm = "Tablet / Capsule",
                    standardDosageEn = "1 tablet 30-60 mins before breakfast & dinner",
                    standardDosageBn = "১টি ট্যাবলেট সকালে ও রাতে খাবারের ৩০ মিনিট আগে",
                    commonBrands = listOf("Nexum", "Maxpro", "Sergel", "Opton", "Esopra"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Take on an empty stomach with a full glass of water.",
                    clinicalTipBn = "খালি পেটে পর্যাপ্ত পানির সাথে সেবন করতে বলুন।"
                ),
                OtcRecommendation(
                    genericName = "Antacid Suspension / Chewable",
                    dosageForm = "Suspension / Chewable Tab",
                    standardDosageEn = "2 teaspoonfuls or 2 tabs 1-2 hours after meals & bedtime",
                    standardDosageBn = "২ চামচ অথবা ২টি ট্যাবলেট খাওয়ার ১ ঘণ্টা পর এবং ঘুমানোর আগে",
                    commonBrands = listOf("Entacyd", "Gelusil", "Marlox", "Adcid"),
                    defaultQty = 1.0,
                    isPrimary = true,
                    clinicalTipEn = "Provides immediate neutralizing relief for active burning sensation.",
                    clinicalTipBn = "তাৎক্ষণিক বুকজ্বালা প্রশমনে অত্যন্ত কার্যকর।"
                ),
                OtcRecommendation(
                    genericName = "Domperidone 10mg",
                    dosageForm = "Tablet",
                    standardDosageEn = "1 tablet 15-30 mins before meals (for nausea/bloating)",
                    standardDosageBn = "১টি ট্যাবলেট খাবার ১৫-৩০ মিনিট আগে (বমি ভাব বা পেট ফাঁপায়)",
                    commonBrands = listOf("Motigut", "Omidon", "Vidon", "Deflux"),
                    defaultQty = 10.0,
                    isPrimary = false,
                    clinicalTipEn = "Do not exceed 30mg daily; avoid if cardiac QT prolongation history.",
                    clinicalTipBn = "পেট ফাঁপা বা বমি বমি ভাব থাকলে খাবারের আগে কার্যকর।"
                )
            )
        ),
        OtcCategory(
            id = "fever",
            titleEn = "Fever, Headache & Body Ache",
            titleBn = "জ্বর, মাথাব্যথা ও শরীর ব্যথা",
            subtitleEn = "Mild-to-moderate fever, headache, joint/muscle aches",
            subtitleBn = "হালকা থেকে তীব্র জ্বর, সর্দির জ্বর, মাথাব্যথা ও গা-হাত-পা ব্যথা",
            redFlagsEn = listOf(
                "High fever > 103°F lasting > 3 days or accompanied by stiff neck / delirium",
                "Petechial rash / bleeding from gums or nose (Dengue hemorrhagic warning)",
                "Severe shortness of breath or persistent chest discomfort"
            ),
            redFlagsBn = listOf(
                "১০৩° ফারেনহাইটের বেশি তীব্র জ্বর ৩ দিনের বেশি থাকা বা ঘাড় শক্ত হওয়া",
                "শরীরে লালচে ছোপ বা মাড়ি/নাক দিয়ে রক্তপাত (ডেঙ্গু হেমোরেজিক ঝুঁকি)",
                "তীব্র শ্বাসকষ্ট বা বুকে অস্বাভাবিক চাপ অনুভূত হওয়া"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Paracetamol 500mg",
                    dosageForm = "Tablet",
                    standardDosageEn = "1-2 tablets every 6-8 hours after food (Max 4g/day)",
                    standardDosageBn = "১-২টি ট্যাবলেট প্রতি ৬-৮ ঘণ্টা পর ভরা পেটে (দৈনিক সর্বোচ্চ ৪ গ্রাম)",
                    commonBrands = listOf("Napa", "Ace", "Fast", "Reset", "Pyrex"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Safest first-line antipyretic. Do NOT prescribe NSAIDs if Dengue is suspected.",
                    clinicalTipBn = "ডেঙ্গুর মৌসুমে ব্যথানাশক হিসেবে অ্যাসপিরিন/আইবুপ্রোফেন বাদ দিয়ে কেবল প্যারাসিটামল দিন।"
                ),
                OtcRecommendation(
                    genericName = "Paracetamol 665mg XR (Extended Release)",
                    dosageForm = "Tablet",
                    standardDosageEn = "1-2 tablets every 8 hours (Max 6 tablets/day)",
                    standardDosageBn = "১-২টি ট্যাবলেট প্রতি ৮ ঘণ্টা পর ভরা পেটে (দৈনিক সর্বোচ্চ ৬টি)",
                    commonBrands = listOf("Napa Extend", "Ace XR", "Fast XR"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Sustained relief for 8 hours; must swallow whole, do not crush.",
                    clinicalTipBn = "৮ ঘণ্টা পর্যন্ত দীর্ঘস্থায়ী কাজ করে। ট্যাবলেট চিবানো বা ভাঙা যাবে না।"
                ),
                OtcRecommendation(
                    genericName = "Paracetamol + Caffeine (500mg+65mg)",
                    dosageForm = "Tablet",
                    standardDosageEn = "1 tablet every 6 hours for tension headache or flu lethargy",
                    standardDosageBn = "১টি ট্যাবলেট প্রতি ৬ ঘণ্টা পর তীব্র মাথাব্যথা বা ফ্লু ক্লান্তির জন্য",
                    commonBrands = listOf("Napa Extra", "Ace Plus", "Fast Plus"),
                    defaultQty = 10.0,
                    isPrimary = false,
                    clinicalTipEn = "Avoid taking right before bedtime due to caffeine wakefulness.",
                    clinicalTipBn = "ক্যাফেইনের কারণে রাতে ঘুমানোর ঠিক আগে খাওয়া এড়িয়ে চলা ভালো।"
                )
            )
        ),
        OtcCategory(
            id = "diarrhea",
            titleEn = "Acute Diarrhea & Dehydration",
            titleBn = "পাতলা পায়খানা ও পানিশূন্যতা",
            subtitleEn = "Watery diarrhea, loose motions, dehydration prevention",
            subtitleBn = "ঘন ঘন পাতলা পায়খানা, দুর্বলতা ও পানিশূন্যতা প্রতিরোধ",
            redFlagsEn = listOf(
                "Visible blood, mucus, or pus in stool (Invasive dysentery / amoebiasis - needs doctor)",
                "Severe dehydration (sunken eyes, skin pinch retracts very slowly, lethargy/unconsciousness)",
                "Inability to retain any oral fluids due to uncontrollable vomiting"
            ),
            redFlagsBn = listOf(
                "পায়খানার সাথে রক্ত বা আম যাওয়া (আমাশয়/ইনফেকশন - চিকিৎসকের পরামর্শ জরুরি)",
                "মারাত্মক পানিশূন্যতা (চোখ কোটরে যাওয়া, চামড়া কুঁচকে থাকা, নিস্তেজ হয়ে পড়া)",
                "তীব্র বমির কারণে কোনো প্রকার স্যালাইন পেটে না রাখা"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Oral Rehydration Salts (ORS)",
                    dosageForm = "Powder Sachet",
                    standardDosageEn = "Mix 1 sachet in exactly 500ml clean water; 1 glass after each loose stool",
                    standardDosageBn = "১ প্যাকেট ঠিক ৫০০ মিলি নিরাপদ পানিতে গুলিয়ে প্রতিবার পায়খানার পর ১ গ্লাস পান করুন",
                    commonBrands = listOf("Orsaline-N", "SMC ORS", "Taste Me ORS"),
                    defaultQty = 5.0,
                    isPrimary = true,
                    clinicalTipEn = "Never boil or mix with warm water/milk. Discard mixed solution after 12 hours.",
                    clinicalTipBn = "কখনোই গরম পানিতে স্যালাইন গুলাবেন না। গুলানো স্যালাইন ১২ ঘণ্টার বেশি রাখবেন না।"
                ),
                OtcRecommendation(
                    genericName = "Zinc 20mg",
                    dosageForm = "Dispersible Tablet / Syrup",
                    standardDosageEn = "1 tablet daily for 10-14 consecutive days",
                    standardDosageBn = "১টি ট্যাবলেট দিনে ১ বার টানা ১০-১৪ দিন",
                    commonBrands = listOf("Baby Zinc", "Zisket", "Zincon", "Zincat"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Accelerates mucosal recovery and prevents recurrence.",
                    clinicalTipBn = "অন্ত্রের দেয়াল পুনর্গঠন করে এবং ডায়রিয়া দ্রুত নিরাময় করে।"
                ),
                OtcRecommendation(
                    genericName = "Probiotic (Saccharomyces boulardii / L. acidophilus)",
                    dosageForm = "Sachet / Capsule",
                    standardDosageEn = "1 sachet/capsule twice daily mixed with room-temperature water",
                    standardDosageBn = "১টি স্যাচেট/ক্যাপসুল দিনে ২ বার স্বাভাবিক তাপমাত্রার পানিতে",
                    commonBrands = listOf("Prebio", "Boulardii", "Bactipro"),
                    defaultQty = 6.0,
                    isPrimary = false,
                    clinicalTipEn = "Restores normal gut flora disrupted by diarrhea.",
                    clinicalTipBn = "ডায়রিয়ায় ক্ষতিগ্রস্ত পেটের উপকারী ব্যাকটেরিয়া পুনরায় তৈরি করে।"
                )
            )
        ),
        OtcCategory(
            id = "firstaid",
            titleEn = "First-Aid, Cuts & Minor Burns",
            titleBn = "কাটা-ছেঁড়া, ক্ষত ও পোড়া (ফার্স্ট এইড)",
            subtitleEn = "Superficial lacerations, scrapes, burns, wound dressing",
            subtitleBn = "হঠাৎ কেটে যাওয়া, ছিলে যাওয়া, পোড়া ক্ষত ও ড্রেসিং সামগ্রী",
            redFlagsEn = listOf(
                "Deep puncture wound or rusted metal cut (High Tetanus risk - Administer Tetanus Toxoid / TT)",
                "Uncontrolled arterial spurting or bleeding persisting > 10 mins with direct pressure",
                "Extensive second/third degree burns, facial/hand burns, or chemical/electrical burns"
            ),
            redFlagsBn = listOf(
                "মরিচাধরা লোহা বা গভীর ক্ষত (ধনুষ্টংকার ঝুঁকি - টিটেনাস টিকা / টিটি নেওয়া বাধ্যতামূলক)",
                "১০ মিনিট চেপে রাখার পরও রক্তপাত বন্ধ না হওয়া",
                "মুখমণ্ডল, হাত বা শরীরের বড় অংশ পুড়ে যাওয়া অথবা কেমিক্যাল/বিদ্যুৎ পোড়া"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Povidone Iodine 10% Solution / Ointment",
                    dosageForm = "Topical Solution / Tube",
                    standardDosageEn = "Clean wound with sterile saline/water, apply solution twice daily",
                    standardDosageBn = "ক্ষত পরিষ্কার করে দিনে ২ বার ক্ষতস্থানে লাগান",
                    commonBrands = listOf("Pevisone", "Povidon", "Polydine", "Viodin"),
                    defaultQty = 1.0,
                    isPrimary = true,
                    clinicalTipEn = "Broad spectrum antiseptic. Does not sting as severely as spirit.",
                    clinicalTipBn = "জীবাণু ধ্বংসকারী এবং ড্রেসিংয়ের জন্য অত্যন্ত নিরাপদ।"
                ),
                OtcRecommendation(
                    genericName = "Silver Sulfadiazine 1% Cream",
                    dosageForm = "Topical Cream",
                    standardDosageEn = "Apply 1-2mm layer over clean burned skin 1-2 times daily",
                    standardDosageBn = "পোড়া স্থানে পরিষ্কার করে দিনে ১-২ বার পাতলা প্রলেপ লাগান",
                    commonBrands = listOf("Burnsil", "Silvadene", "Burnex", "Dermazin"),
                    defaultQty = 1.0,
                    isPrimary = true,
                    clinicalTipEn = "First-line topical cream for minor superficial burns. Never apply ice directly.",
                    clinicalTipBn = "পোড়া ক্ষতের সেরা মলম। কখনো পোড়া জায়গায় সরাসরি বরফ বা টুথপেস্ট লাগাবেন না।"
                ),
                OtcRecommendation(
                    genericName = "Sterile Gauze + Micropore Tape + Band-Aid (Dressing Kit)",
                    dosageForm = "Dressing Essentials",
                    standardDosageEn = "Cover wound to protect from airborne dust, flies and infection",
                    standardDosageBn = "ধূলাবালি ও মাছি থেকে ক্ষত সুরক্ষিত রাখতে ব্যান্ডেজ করুন",
                    commonBrands = listOf("Band-Aid", "Sterile Gauze", "Micropore Tape"),
                    defaultQty = 1.0,
                    isPrimary = true,
                    clinicalTipEn = "Keep dressed area clean and dry. Change dressing daily.",
                    clinicalTipBn = "ব্যান্ডেজ সবসময় শুকনো রাখুন এবং প্রতিদিন পরিবর্তন করুন।"
                )
            )
        ),
        OtcCategory(
            id = "allergy",
            titleEn = "Allergy, Itching & Sneezing",
            titleBn = "এলার্জি, চুলকানি ও সর্দি",
            subtitleEn = "Hives, allergic rhinitis, insect bites, contact dermatitis",
            subtitleBn = "চুলকানি, আমবাত, এলার্জিক সর্দি-হাঁচি ও পোকার কামড়",
            redFlagsEn = listOf(
                "Swelling of lips, tongue, throat, or facial edema (Angioedema / Anaphylaxis - EMERGENCY)",
                "Severe difficulty breathing, stridor or sudden drop in blood pressure",
                "Widespread blistering / peeling rash accompanied by high fever (Steven-Johnson Syndrome)"
            ),
            redFlagsBn = listOf(
                "ঠোঁট, জিহ্বা বা গলা ফুলে যাওয়া ও কথা বলতে কষ্ট (অ্যানাফাইল্যাক্সিস - জরুরি ইমার্জেন্সি!)",
                "তীব্র শ্বাসকষ্ট বা বুকের ভেতর সাঁই-সাঁই শব্দ হওয়া",
                "জ্বর সহ সারা শরীরে ফোসকা পড়া বা চামড়া উঠে যাওয়া"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Fexofenadine 120mg",
                    dosageForm = "Tablet",
                    standardDosageEn = "1 tablet once daily with water (non-drowsy)",
                    standardDosageBn = "১টি ট্যাবলেট দিনে ১ বার (ঘুম ঘুম ভাব কম হয়)",
                    commonBrands = listOf("Fexo", "Telfast", "Fexoral", "Fixal", "Fenofex"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Second-generation antihistamine with negligible sedation. Safe for drivers.",
                    clinicalTipBn = "কাজে বা গাড়ি চালানোর সময় এটি নিরাপদ কারণ এতে ঝিমুনি কম হয়।"
                ),
                OtcRecommendation(
                    genericName = "Bilastine 20mg",
                    dosageForm = "Tablet",
                    standardDosageEn = "1 tablet once daily on an empty stomach (1 hr before or 2 hrs after meal)",
                    standardDosageBn = "১টি ট্যাবলেট দিনে ১ বার খালি পেটে (খাওয়ার ১ ঘণ্টা আগে বা ২ ঘণ্টা পর)",
                    commonBrands = listOf("Bilaxten", "Bilatin", "Bila", "Bilfast"),
                    defaultQty = 10.0,
                    isPrimary = true,
                    clinicalTipEn = "Rapid onset within 30 minutes for allergic urticaria/itching.",
                    clinicalTipBn = "চুলকানি ও এলার্জিতে দ্রুত ৩০ মিনিটের মধ্যে কাজ শুরু করে।"
                ),
                OtcRecommendation(
                    genericName = "Calamine Lotion",
                    dosageForm = "Topical Lotion",
                    standardDosageEn = "Shake well; apply locally to itchy skin with clean cotton 2-3 times daily",
                    standardDosageBn = "ভালো করে ঝাঁকিয়ে তুলার সাহায্যে চুলকানির জায়গায় দিনে ২-৩ বার লাগান",
                    commonBrands = listOf("Calamine", "Caladryl", "Calasoothe"),
                    defaultQty = 1.0,
                    isPrimary = false,
                    clinicalTipEn = "Soothes irritated skin and insect bites through natural cooling sensation.",
                    clinicalTipBn = "ত্বকের জ্বালাপোড়া ও চুলকানিতে চমৎকার শীতল অনুভূতি দেয়।"
                )
            )
        ),
        OtcCategory(
            id = "eyecare",
            titleEn = "Eye Redness & Ear Discomfort",
            titleBn = "চোখ লাল, খচখচ ও কান ব্যথা",
            subtitleEn = "Dry eyes, computer eye strain, dust irritation, superficial ear wax",
            subtitleBn = "চোখ শুষ্ক বা লাল হওয়া, ময়লা পড়ার অস্বস্তি ও সাধারণ কান ব্যথা",
            redFlagsEn = listOf(
                "Sudden partial or complete vision loss, halos around lights, or extreme eye pain",
                "Corneal ulceration, chemical splash into eyes (Requires immediate copious saline irrigation & eye hospital)",
                "Yellow/bloody discharge from ear canal following head trauma"
            ),
            redFlagsBn = listOf(
                "হঠাৎ দৃষ্টিশক্তি হ্রাস পাওয়া বা চোখে অসহ্য তীব্র ব্যথা (গ্লুকোমা/আলসার সতর্কতা)",
                "চোখে ক্ষতিকারক কেমিক্যাল পড়া (দ্রুত প্রচুর পানি দিয়ে ধুয়ে চক্ষু হাসপাতালে যান)",
                "মাথায় আঘাতের পর কান দিয়ে রক্ত বা তরল পুঁজ বের হওয়া"
            ),
            recommendations = listOf(
                OtcRecommendation(
                    genericName = "Carboxymethylcellulose 0.5% (Artificial Tears)",
                    dosageForm = "Eye Drops",
                    standardDosageEn = "1-2 drops into affected eye(s) 3-4 times daily as needed",
                    standardDosageBn = "১-২ ফোঁটা আক্রান্ত চোখে দিনে ৩-৪ বার প্রয়োজনমতো",
                    commonBrands = listOf("Tears Naturale", "Optive", "Comfort Tears", "Refresh"),
                    defaultQty = 1.0,
                    isPrimary = true,
                    clinicalTipEn = "Preserves ocular tear film. Never touch bottle dropper tip to eyelashes.",
                    clinicalTipBn = "চোখ ভেজা ও আরামদায়ক রাখে। ড্রপারের মুখ কখনো চোখের পাতায় লাগাবেন না।"
                ),
                OtcRecommendation(
                    genericName = "Antazoline + Tetrahydrozoline Eye Drops",
                    dosageForm = "Eye Drops",
                    standardDosageEn = "1 drop 2-3 times daily for allergic conjunctival redness (Max 5 days)",
                    standardDosageBn = "১ ফোঁটা দিনে ২-৩ বার এলার্জিক লাল চোখের জন্য (সর্বোচ্চ ৫ দিন)",
                    commonBrands = listOf("Vasoclear", "Opcon-A", "Histacel"),
                    defaultQty = 1.0,
                    isPrimary = false,
                    clinicalTipEn = "Do not use for more than 5 consecutive days to prevent rebound hyperaemia.",
                    clinicalTipBn = "টানা ৫ দিনের বেশি ব্যবহার করবেন না।"
                )
            )
        )
    )
}
