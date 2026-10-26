package com.techyfiposmeds.data.clinical

import androidx.compose.ui.graphics.Color

/**
 * WHO, Bangladesh Society of Hypertension, and Diabetic Association of Bangladesh (BADAS)
 * clinical guidelines for patient vitals triage and classification.
 */
object PatientVitalsHelper {

    enum class BpClassification(
        val labelEn: String,
        val labelBn: String,
        val colorHex: Long,
        val adviceEn: String,
        val adviceBn: String
    ) {
        HYPOTENSION(
            "Low Blood Pressure (Hypotension)",
            "নিম্ন রক্তচাপ (হাইপোটেনশন)",
            0xFF3B82F6,
            "Ensure adequate hydration and salt intake. Seek medical evaluation if dizzy.",
            "পর্যাপ্ত পানি ও ওরস্যালাইন পান করুন। মাথা ঘুরলে দ্রুত চিকিৎসকের পরামর্শ নিন।"
        ),
        NORMAL(
            "Normal Blood Pressure",
            "স্বাভাবিক রক্তচাপ",
            0xFF10B981,
            "Optimal cardiovascular pressure. Maintain healthy diet and active lifestyle.",
            "চমৎকার ও স্বাভাবিক রক্তচাপ। সুষম খাদ্য ও নিয়মিত হাঁটার অভ্যাস বজায় রাখুন।"
        ),
        ELEVATED(
            "Elevated (Pre-hypertension)",
            "উচ্চ-স্বাভাবিক (প্রি-হাইপারটেনশন)",
            0xFFEAB308,
            "Slightly above normal. Reduce dietary salt and manage stress.",
            "স্বাভাবিকের চেয়ে সামান্য বেশি। খাবারে কাঁচা লবণ পরিহার করুন ও ওজন নিয়ন্ত্রণে রাখুন।"
        ),
        STAGE_1_HTN(
            "Stage 1 Hypertension",
            "স্টেজ-১ উচ্চ রক্তচাপ (মৃদু)",
            0xFFF97316,
            "Mild hypertension. Consult a doctor for lifestyle modifications and monitoring.",
            "মৃদু উচ্চ রক্তচাপ। চিকিৎসকের পরামর্শ নিয়ে জীবনযাত্রার পরিবর্তন ও নিয়মিত মনিটর করুন।"
        ),
        STAGE_2_HTN(
            "Stage 2 Hypertension",
            "স্টেজ-২ উচ্চ রক্তচাপ (মাঝারি/তীব্র)",
            0xFFEF4444,
            "Significant hypertension. Doctor consultation and antihypertensive medication needed.",
            "মাঝারি/তীব্র উচ্চ রক্তচাপ। অবিলম্বে চিকিৎসকের পরামর্শ অনুযায়ী ওষুধ সেবন নিশ্চিত করুন।"
        ),
        HYPERTENSIVE_CRISIS(
            "Hypertensive Crisis (Emergency!)",
            "মারাত্মক রক্তচাপ সংকট (জরুরি বিপদ!)",
            0xFFDC2626,
            "Dangerously high BP! Seek emergency medical attention immediately.",
            "বিপজ্জনকভাবে উচ্চ রক্তচাপ! অবিলম্বে নিকটস্থ হাসপাতালের জরুরি বিভাগে যান।"
        )
    }

    enum class SugarClassification(
        val labelEn: String,
        val labelBn: String,
        val colorHex: Long,
        val adviceEn: String,
        val adviceBn: String
    ) {
        HYPOGLYCEMIA(
            "Hypoglycemia (Dangerously Low)",
            "হাইপোগ্লাইসেমিয়া (মারাত্মক কম সুগার)",
            0xFFDC2626,
            "Immediate sugar intake required (fruit juice, glucose, sweet). Dangerous if untreated.",
            "জরুরিভাবে চিনি, গ্লুকোজ বা মিষ্টি খাবার খান। দ্রুত সুগার না বাড়ালে বিপদ হতে পারে।"
        ),
        NORMAL(
            "Normal Blood Sugar",
            "স্বাভাবিক ব্লাড সুগার",
            0xFF10B981,
            "Healthy glycemic control within standard clinical targets.",
            "স্বাভাবিক ও চমৎকার সুগার লেভেল। সঠিক খাদ্যাভ্যাস ও স্বাস্থ্যকর নিয়ম বজায় রাখুন।"
        ),
        PRE_DIABETES(
            "Impaired Glucose (Pre-Diabetes)",
            "প্রি-ডায়াবেটিস (ডায়াবেটিসের পূর্ব লক্ষণ)",
            0xFFF59E0B,
            "Higher than normal. Reduce sugar and refined carbohydrates; exercise regularly.",
            "স্বাভাবিকের চেয়ে বেশি। মিষ্টি ও অতিরিক্ত শর্করাযুক্ত খাবার এড়িয়ে চলুন ও নিয়মিত হাঁটুন।"
        ),
        DIABETES(
            "Elevated (Diabetes Range)",
            "উচ্চ ব্লাড সুগার (ডায়াবেটিস রেঞ্জ)",
            0xFFEF4444,
            "High glucose level. Strict dietary adherence and medical follow-up recommended.",
            "ডায়াবেটিস সীমার বেশি। ডাক্তারের পরামর্শে সঠিক অ্যান্টিডায়াবেটিক ওষুধ ও সুষম ডায়েট মেনে চলুন।"
        )
    }

    enum class BmiClassification(
        val labelEn: String,
        val labelBn: String,
        val colorHex: Long
    ) {
        UNDERWEIGHT("Underweight", "কম ওজন", 0xFF3B82F6),
        NORMAL("Normal Weight", "স্বাভাবিক ওজন", 0xFF10B981),
        OVERWEIGHT("Overweight", "অতিরিক্ত ওজন", 0xFFF59E0B),
        OBESE("Obese", "স্থূলতা (অতিরিক্ত মেদ)", 0xFFEF4444)
    }

    fun classifyBloodPressure(systolic: Int, diastolic: Int): BpClassification {
        return when {
            systolic > 180 || diastolic > 120 -> BpClassification.HYPERTENSIVE_CRISIS
            systolic >= 140 || diastolic >= 90 -> BpClassification.STAGE_2_HTN
            systolic in 130..139 || diastolic in 80..89 -> BpClassification.STAGE_1_HTN
            systolic in 120..129 && diastolic < 80 -> BpClassification.ELEVATED
            systolic < 90 || diastolic < 60 -> BpClassification.HYPOTENSION
            else -> BpClassification.NORMAL
        }
    }

    fun classifyBloodSugar(mmolL: Double, testType: String): SugarClassification {
        if (mmolL < 4.0) return SugarClassification.HYPOGLYCEMIA

        val isFasting = testType.equals("FBS", ignoreCase = true)
        return if (isFasting) {
            when {
                mmolL >= 7.0 -> SugarClassification.DIABETES
                mmolL >= 5.6 -> SugarClassification.PRE_DIABETES
                else -> SugarClassification.NORMAL
            }
        } else {
            // RBS or 2HABF
            when {
                mmolL >= 11.1 -> SugarClassification.DIABETES
                mmolL >= 7.8 -> SugarClassification.PRE_DIABETES
                else -> SugarClassification.NORMAL
            }
        }
    }

    fun calculateBmi(weightKg: Double, heightCm: Double): Double {
        if (heightCm <= 0.0 || weightKg <= 0.0) return 0.0
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        return (bmi * 10).toInt() / 10.0 // round to 1 decimal place
    }

    fun classifyBmi(bmi: Double): BmiClassification {
        return when {
            bmi <= 0.0 -> BmiClassification.NORMAL
            bmi < 18.5 -> BmiClassification.UNDERWEIGHT
            bmi < 23.0 -> BmiClassification.NORMAL     // WHO Asian standard
            bmi < 27.5 -> BmiClassification.OVERWEIGHT // WHO Asian standard
            else -> BmiClassification.OBESE
        }
    }

    fun mmolToMgDl(mmolL: Double): Double {
        return (mmolL * 18.018 * 10).toInt() / 10.0
    }
}
