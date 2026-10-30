package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.QuestionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StudySaathi", appName)
  }

  @Test
  fun `insert and retrieve saved question in database`() = runBlocking {
    val entity = QuestionEntity(
      questionText = "Solve 2x + 4 = 10",
      subject = "Maths",
      difficulty = "Easy",
      conceptSummary = "Linear Equations",
      stepsJson = "[]",
      finalAnswer = "x = 3",
      simpleLearningExplanation = "Balance both sides like a weighing scale",
      practiceQuestionsJson = "[]",
      didYouKnowFact = "Algebra comes from al-jabr"
    )

    val id = db.questionDao().insertQuestion(entity)
    val questions = db.questionDao().getAllQuestions().first()

    assertEquals(1, questions.size)
    assertEquals("Solve 2x + 4 = 10", questions[0].questionText)
    assertEquals("x = 3", questions[0].finalAnswer)

    // Test bookmark toggle
    db.questionDao().updateBookmarkStatus(id, true)
    val bookmarked = db.questionDao().getBookmarkedQuestions().first()
    assertEquals(1, bookmarked.size)
  }
}
