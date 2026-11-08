package com.moobehgames.snoresafe.data

import com.moobehgames.snoresafe.core.audio.AudioClip
import com.moobehgames.snoresafe.core.audio.ClipLabel
import com.moobehgames.snoresafe.core.ml.PersonalHead
import com.moobehgames.snoresafe.core.ml.PersonalHeadWeights
import com.moobehgames.snoresafe.core.ml.PersonalTraining
import com.moobehgames.snoresafe.core.ml.TrainingExample
import com.moobehgames.snoresafe.core.session.SessionCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The personal snore model, trained on this phone from the user's labels.
 * Labels are kept in their own training set, so cleaning up old audio never erases what was learned.
 */
class PersonalModelStore(private val dir: File, private val audio: AudioStore) {
    private val modelFile get() = File(dir, "model.json")
    private val setFile get() = File(dir, "training-set.json")

    private val _weights = MutableStateFlow(loadModel())
    val weights: StateFlow<PersonalHeadWeights?> = _weights.asStateFlow()

    private val _counts = MutableStateFlow(PersonalTraining.counts(loadSet()))
    val counts: StateFlow<PersonalTraining.LabelCounts> = _counts.asStateFlow()

    /** Labels [clip] (null clears it) and retrains. Training a few hundred examples takes well under a second. */
    @Synchronized
    fun label(sessionId: String, clip: AudioClip, label: ClipLabel?) {
        audio.setUserLabel(sessionId, clip.id, label)
        val set = PersonalTraining.update(loadSet(), clip, label)
        saveSet(set)
        retrain(set)
    }

    /** Current labels, for backups. */
    @Synchronized
    fun exportSet(): List<TrainingExample> = loadSet()

    /** Merges labels from a backup (the phone's own answers win) and retrains. */
    @Synchronized
    fun importSet(restored: List<TrainingExample>) {
        val current = loadSet()
        val merged = (current + restored.filter { r -> current.none { it.clipId == r.clipId } }).takeLast(PersonalTraining.MAX_EXAMPLES)
        saveSet(merged)
        retrain(merged)
    }

    @Synchronized
    fun reset() {
        audio.all().forEach { night ->
            if (night.clips.any { it.userLabel != null }) audio.write(night.copy(clips = night.clips.map { it.copy(userLabel = null) }))
        }
        setFile.delete()
        modelFile.delete()
        _weights.value = null
        _counts.value = PersonalTraining.LabelCounts(0, 0)
    }

    private fun retrain(set: List<TrainingExample>) {
        val trained = PersonalHead.train(PersonalTraining.fromSet(set))
        if (trained == null) modelFile.delete() else modelFile.writeTextAtomically(SessionCodec.encodeHead(trained))
        _weights.value = trained
        _counts.value = PersonalTraining.counts(set)
    }

    private fun saveSet(set: List<TrainingExample>) = setFile.writeTextAtomically(SessionCodec.encodeTrainingSet(set))

    private fun loadSet(): List<TrainingExample> =
        if (setFile.isFile) runCatching { SessionCodec.decodeTrainingSet(setFile.readText()) }.getOrDefault(emptyList()) else emptyList()

    private fun loadModel(): PersonalHeadWeights? =
        if (modelFile.isFile) runCatching { SessionCodec.decodeHead(modelFile.readText()) }.getOrNull() else null
}
