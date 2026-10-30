package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.QuestionDao
import com.example.data.local.QuestionEntity
import com.example.data.model.PracticeQuestion
import com.example.data.model.SolutionResponse
import com.example.data.model.StepExplanation
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "StudySaathiRepo"

class StudySaathiRepository(private val questionDao: QuestionDao) {

    private val stepSchema = Schema.obj(
        properties = mapOf(
            "stepNumber" to Schema.integer(description = "Step sequence number starting at 1"),
            "title" to Schema.string(description = "Short title for this step"),
            "explanation" to Schema.string(description = "Detailed explanation of this step"),
            "formulaOrTip" to Schema.string(description = "Relevant formula, theorem, or tip", nullable = true)
        ),
        optionalProperties = listOf("formulaOrTip")
    )

    private val practiceQuestionSchema = Schema.obj(
        properties = mapOf(
            "id" to Schema.integer(description = "Question index"),
            "question" to Schema.string(description = "The practice problem statement"),
            "hint" to Schema.string(description = "Helpful hint without giving away the direct answer"),
            "answer" to Schema.string(description = "The final correct answer"),
            "explanation" to Schema.string(description = "Brief solution steps for this practice problem")
        )
    )

    private val solutionSchema = Schema.obj(
        properties = mapOf(
            "recognizedQuestion" to Schema.string(description = "The full text of the question analyzed (from image OCR or text)"),
            "subject" to Schema.string(description = "Classified subject: Maths, Science, English, or Reasoning"),
            "difficulty" to Schema.string(description = "Difficulty level: Easy, Medium, or Hard"),
            "conceptSummary" to Schema.string(description = "Clear 1-2 sentence summary of the core concept or formula used"),
            "steps" to Schema.array(
                items = stepSchema,
                description = "Step by step logical solution broken down clearly"
            ),
            "finalAnswer" to Schema.string(description = "Concise highlighted final answer"),
            "simpleLearningExplanation" to Schema.string(description = "Intuitive explanation in simple conversational language with an easy real-life analogy (Saral Bhasha)"),
            "practiceQuestions" to Schema.array(
                items = practiceQuestionSchema,
                description = "3 to 4 similar practice questions to test understanding"
            ),
            "didYouKnowFact" to Schema.string(description = "A fascinating educational fact related to this subject concept")
        ),
        optionalProperties = listOf("didYouKnowFact")
    )

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = solutionSchema
            }
        )
    }

    suspend fun solveQuestion(
        typedQuestion: String,
        imageBitmap: Bitmap?,
        selectedSubject: String,
        selectedDifficulty: String,
        simpleLearningMode: Boolean,
        explanationLanguage: String
    ): Result<SolutionResponse> = withContext(Dispatchers.IO) {
        try {
            val promptBuilder = StringBuilder()
            promptBuilder.append("You are StudySaathi, an expert, encouraging student tutor and solver.\n")
            promptBuilder.append("Target Subject: $selectedSubject\n")
            promptBuilder.append("Target Difficulty Level: $selectedDifficulty\n")
            promptBuilder.append("Simple Learning Mode Enabled: $simpleLearningMode\n")
            promptBuilder.append("Language Style: $explanationLanguage (e.g. English, or Hindi/Hinglish for Indian students if selected).\n")
            
            if (typedQuestion.isNotBlank()) {
                promptBuilder.append("Question statement: $typedQuestion\n")
            }
            if (imageBitmap != null) {
                promptBuilder.append("Please carefully scan and read the textbook/worksheet question in the provided photo image. Transcribe it accurately in recognizedQuestion, and solve it step-by-step.\n")
            }
            promptBuilder.append("Provide a complete, accurate, rigorous step-by-step solution. Include clear steps, a clear final answer, a simple learning breakdown (Saral Bhasha with real life analogy), and 3-4 practice questions with hints and answers.\n")

            val prompt = promptBuilder.toString()

            val responseText: String = withTimeout(60_000L) {
                if (imageBitmap != null) {
                    val multimodalContent = content {
                        image(imageBitmap)
                        text(prompt)
                    }
                    val resp = generativeModel.generateContent(multimodalContent)
                    resp.text ?: throw IllegalStateException("Model returned empty text.")
                } else {
                    val resp = generativeModel.generateContent(prompt)
                    resp.text ?: throw IllegalStateException("Model returned empty text.")
                }
            }

            val solution = parseSolutionJson(responseText, selectedSubject, selectedDifficulty)

            // Save to local Room database for offline review
            saveToOfflineHistory(solution, hasImage = (imageBitmap != null))

            Result.success(solution)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Question solving timed out", e)
            Result.failure(Exception("AI Solver timed out after 60 seconds. Please check your internet connection and try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in solveQuestion", e)
            Result.failure(e)
        }
    }

    private fun parseSolutionJson(jsonString: String, fallbackSubject: String, fallbackDifficulty: String): SolutionResponse {
        val root = JSONObject(jsonString)
        val recognizedQuestion = root.optString("recognizedQuestion", "")
        val subject = root.optString("subject", fallbackSubject)
        val difficulty = root.optString("difficulty", fallbackDifficulty)
        val conceptSummary = root.optString("conceptSummary", "Core Concept")
        val finalAnswer = root.optString("finalAnswer", "")
        val simpleLearningExplanation = root.optString("simpleLearningExplanation", "")
        val didYouKnowFact = root.optString("didYouKnowFact", "")

        val stepsList = mutableListOf<StepExplanation>()
        val stepsArray = root.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until stepsArray.length()) {
            val stepObj = stepsArray.getJSONObject(i)
            stepsList.add(
                StepExplanation(
                    stepNumber = stepObj.optInt("stepNumber", i + 1),
                    title = stepObj.optString("title", "Step ${i + 1}"),
                    explanation = stepObj.optString("explanation", ""),
                    formulaOrTip = stepObj.optString("formulaOrTip").takeIf { it.isNotBlank() }
                )
            )
        }

        val practiceList = mutableListOf<PracticeQuestion>()
        val practiceArray = root.optJSONArray("practiceQuestions") ?: JSONArray()
        for (i in 0 until practiceArray.length()) {
            val pObj = practiceArray.getJSONObject(i)
            practiceList.add(
                PracticeQuestion(
                    id = pObj.optInt("id", i + 1),
                    question = pObj.optString("question", ""),
                    hint = pObj.optString("hint", ""),
                    answer = pObj.optString("answer", ""),
                    explanation = pObj.optString("explanation", "")
                )
            )
        }

        return SolutionResponse(
            recognizedQuestion = recognizedQuestion,
            subject = subject,
            difficulty = difficulty,
            conceptSummary = conceptSummary,
            steps = stepsList,
            finalAnswer = finalAnswer,
            simpleLearningExplanation = simpleLearningExplanation,
            practiceQuestions = practiceList,
            didYouKnowFact = didYouKnowFact
        )
    }

    private suspend fun saveToOfflineHistory(solution: SolutionResponse, hasImage: Boolean) {
        try {
            val stepsJson = JSONArray().apply {
                solution.steps.forEach { step ->
                    put(JSONObject().apply {
                        put("stepNumber", step.stepNumber)
                        put("title", step.title)
                        put("explanation", step.explanation)
                        put("formulaOrTip", step.formulaOrTip ?: "")
                    })
                }
            }.toString()

            val practiceJson = JSONArray().apply {
                solution.practiceQuestions.forEach { pq ->
                    put(JSONObject().apply {
                        put("id", pq.id)
                        put("question", pq.question)
                        put("hint", pq.hint)
                        put("answer", pq.answer)
                        put("explanation", pq.explanation)
                    })
                }
            }.toString()

            val entity = QuestionEntity(
                questionText = solution.recognizedQuestion.ifBlank { "Textbook Question" },
                subject = solution.subject,
                difficulty = solution.difficulty,
                conceptSummary = solution.conceptSummary,
                stepsJson = stepsJson,
                finalAnswer = solution.finalAnswer,
                simpleLearningExplanation = solution.simpleLearningExplanation,
                practiceQuestionsJson = practiceJson,
                didYouKnowFact = solution.didYouKnowFact,
                hasImage = hasImage
            )
            questionDao.insertQuestion(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache question in local Room DB", e)
        }
    }

    fun getAllSavedQuestions(): Flow<List<QuestionEntity>> = questionDao.getAllQuestions()
    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>> = questionDao.getBookmarkedQuestions()
    fun searchQuestions(query: String): Flow<List<QuestionEntity>> = questionDao.searchQuestions(query)
    suspend fun toggleBookmark(id: Long, isBookmarked: Boolean) = questionDao.updateBookmarkStatus(id, isBookmarked)
    suspend fun deleteQuestion(entity: QuestionEntity) = questionDao.deleteQuestion(entity)
    suspend fun clearHistory() = questionDao.clearAll()
}
