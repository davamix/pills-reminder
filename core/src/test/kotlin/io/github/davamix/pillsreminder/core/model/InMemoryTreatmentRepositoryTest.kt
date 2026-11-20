package io.github.davamix.pillsreminder.core.model

import io.github.davamix.pillsreminder.core.Example
import io.github.davamix.pillsreminder.core.MADRID
import io.github.davamix.pillsreminder.core.actions.clearAnswer
import io.github.davamix.pillsreminder.core.actions.deleteTreatment
import io.github.davamix.pillsreminder.core.actions.endTreatment
import io.github.davamix.pillsreminder.core.actions.markTaken
import io.github.davamix.pillsreminder.core.actions.saveTreatment
import io.github.davamix.pillsreminder.core.actions.setTakenTime
import io.github.davamix.pillsreminder.core.actions.skip
import io.github.davamix.pillsreminder.core.nov
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDateTime

class InMemoryTreatmentRepositoryTest {
    private fun clockAt(local: LocalDateTime): Clock = Clock.fixed(local.atZone(MADRID).toInstant(), MADRID)

    private suspend fun TreatmentRepository.current(): PillsData = (data.first() as StoredData.Readable).data

    private val k0800 = TakeKey(Example.amoxicillinId, nov(23, 8))

    @Test
    fun `it starts empty, and emits the data and then every change`() =
        runTest {
            val repository = InMemoryTreatmentRepository()
            val seen = async { repository.data.take(3).toList() }
            runCurrent()

            val withExample = repository.update { Example.data }
            runCurrent()
            val ended = repository.update { it.copy(treatments = it.treatments.map { t -> t.copy(endedAt = nov(25, 12)) }) }

            assertEquals(
                listOf(StoredData.Readable(PillsData()), StoredData.Readable(withExample), StoredData.Readable(ended)),
                seen.await(),
            )
        }

    @Test
    fun `concurrent updates don't lose one another`() =
        runTest {
            val repository = InMemoryTreatmentRepository()
            withContext(Dispatchers.Default) {
                (1..200)
                    .map { i ->
                        launch {
                            repository.update { data ->
                                data.copy(
                                    treatments =
                                        data.treatments + Example.treatment.copy(id = TreatmentId("t$i"), medicines = emptyList()),
                                )
                            }
                        }
                    }.joinAll()
            }

            assertEquals(
                200,
                repository
                    .current()
                    .treatments
                    .map { it.id }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun `unreadable data can't be updated, and stays as it is`() =
        runTest {
            val repository = InMemoryTreatmentRepository(StoredData.Unreadable)
            var called = false

            assertThrows<UnreadableDataException> {
                repository.update {
                    called = true
                    it
                }
            }
            assertThrows<UnreadableDataException> { repository.markTaken(k0800, clockAt(nov(23, 8))) }
            assertFalse(called)
            assertEquals(StoredData.Unreadable, repository.data.first())
        }

    @Test
    fun `the helpers save a treatment, answer its takes, end it and delete it, reading the given clock`() =
        runTest {
            val repository = InMemoryTreatmentRepository()

            repository.saveTreatment(Example.treatment.toDraft(), clockAt(nov(22, 12)))
            assertEquals(Example.data, repository.current())

            assertTrue(repository.markTaken(k0800, clockAt(nov(23, 8, 20))))
            assertEquals(Answer.Taken(nov(23, 8, 20)), repository.current().answers[k0800])
            assertTrue(repository.setTakenTime(k0800, nov(23, 8, 10), clockAt(nov(23, 9))))
            assertEquals(Answer.Taken(nov(23, 8, 10)), repository.current().answers[k0800])
            assertTrue(repository.skip(k0800, clockAt(nov(23, 9))))
            assertTrue(repository.clearAnswer(k0800, clockAt(nov(23, 9))))
            assertEquals(emptyMap<TakeKey, Answer>(), repository.current().answers)

            assertTrue(repository.endTreatment(Example.treatmentId, clockAt(nov(25, 12))))
            assertEquals(nov(25, 12), repository.current().treatment(Example.treatmentId)?.endedAt)
            assertTrue(repository.deleteTreatment(Example.treatmentId))
            assertEquals(PillsData(), repository.current())
        }

    @Test
    fun `the helpers return false and change nothing when an action is refused`() =
        runTest {
            val repository = InMemoryTreatmentRepository(StoredData.Readable(Example.data))
            val missed = clockAt(nov(23, 16))

            assertFalse(repository.markTaken(k0800, missed))
            assertFalse(repository.skip(k0800, missed))
            assertFalse(repository.clearAnswer(k0800, missed))
            assertFalse(repository.setTakenTime(k0800, nov(23, 8), missed))
            assertFalse(repository.deleteTreatment(TreatmentId("unknown")))
            assertTrue(repository.endTreatment(Example.treatmentId, missed))
            assertFalse(repository.endTreatment(Example.treatmentId, missed))
            assertEquals(nov(23, 16), repository.current().treatment(Example.treatmentId)?.endedAt)
            assertEquals(emptyMap<TakeKey, Answer>(), repository.current().answers)
        }
}
