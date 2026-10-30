package com.example.data.model

data class StepExplanation(
    val stepNumber: Int,
    val title: String,
    val explanation: String,
    val formulaOrTip: String? = null
)

data class PracticeQuestion(
    val id: Int,
    val question: String,
    val hint: String,
    val answer: String,
    val explanation: String
)

data class SolutionResponse(
    val recognizedQuestion: String,
    val subject: String,
    val difficulty: String,
    val conceptSummary: String,
    val steps: List<StepExplanation>,
    val finalAnswer: String,
    val simpleLearningExplanation: String,
    val practiceQuestions: List<PracticeQuestion>,
    val didYouKnowFact: String = ""
)
