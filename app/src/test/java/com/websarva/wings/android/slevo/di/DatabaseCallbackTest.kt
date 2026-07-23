package com.websarva.wings.android.slevo.di

import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreCompletionChecker
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import javax.inject.Provider

/**
 * [DatabaseCallback] の completion checker launch 境界を検証する。
 *
 * provider 取得と checker 実行から漏れた例外を operational failure として隔離し、
 * cancellation と fatal throwable は別扱いにする契約を固定する。
 */
class DatabaseCallbackTest {

    @Test
    fun completionCheckerBoundary_normalCompletionDoesNotLog() = runTest {
        val checker = mockk<PendingRestoreCompletionChecker>()
        every { checker.runIfNeeded() } just Runs
        val messages = mutableListOf<String>()

        runPendingRestoreCompletionCheckerWithBoundary(
            provider = providerOf(checker),
            logException = messages::add,
        )

        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun completionCheckerBoundary_operationalExceptionIsSanitizedAndContained() = runTest {
        val checker = mockk<PendingRestoreCompletionChecker>()
        every { checker.runIfNeeded() } throws IllegalStateException("secret path")
        val messages = mutableListOf<String>()

        runPendingRestoreCompletionCheckerWithBoundary(
            provider = providerOf(checker),
            logException = messages::add,
        )

        assertEquals(
            listOf("pending restore completion checker failed: IllegalStateException"),
            messages,
        )
        assertFalse(messages.single().contains("secret path"))
    }

    @Test
    fun completionCheckerBoundary_providerExceptionIsSanitizedAndContained() = runTest {
        val messages = mutableListOf<String>()
        val provider = object : Provider<PendingRestoreCompletionChecker> {
            override fun get(): PendingRestoreCompletionChecker {
                throw IllegalArgumentException("secret provider detail")
            }
        }

        runPendingRestoreCompletionCheckerWithBoundary(provider, messages::add)

        assertEquals(
            listOf("pending restore completion checker failed: IllegalArgumentException"),
            messages,
        )
        assertFalse(messages.single().contains("secret provider detail"))
    }

    @Test
    fun completionCheckerBoundary_cancellationIsRethrown() = runTest {
        val checker = mockk<PendingRestoreCompletionChecker>()
        val cancellation = CancellationException("cancelled")
        every { checker.runIfNeeded() } throws cancellation
        val messages = mutableListOf<String>()

        val thrown = try {
            runPendingRestoreCompletionCheckerWithBoundary(
                provider = providerOf(checker),
                logException = messages::add,
            )
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun completionCheckerBoundary_fatalThrowableIsNotCaught() = runTest {
        val checker = mockk<PendingRestoreCompletionChecker>()
        val fatal = AssertionError("fatal")
        every { checker.runIfNeeded() } throws fatal
        val messages = mutableListOf<String>()

        val thrown = try {
            runPendingRestoreCompletionCheckerWithBoundary(
                provider = providerOf(checker),
                logException = messages::add,
            )
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, thrown)
        assertEquals(emptyList<String>(), messages)
    }

    private fun providerOf(checker: PendingRestoreCompletionChecker) =
        object : Provider<PendingRestoreCompletionChecker> {
            override fun get(): PendingRestoreCompletionChecker = checker
        }
}
