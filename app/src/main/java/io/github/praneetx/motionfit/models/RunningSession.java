package io.github.praneetx.motionfit.models;

import io.github.praneetx.motionfit.logic.RunningStatus;

/**
 * The single durable in-progress Running session.
 *
 * <p>The table only ever holds row {@code id = 1}. Elapsed times are
 * {@code SystemClock.elapsedRealtime()} values; durations are milliseconds. The
 * model is a plain data holder so the service/controller can decide transitions
 * with the pure clock and the repository can persist them.</p>
 */
public class RunningSession {

    /** The only legal primary key for the singleton session row. */
    public static final long SINGLETON_ID = 1L;

    private long id = SINGLETON_ID;
    private RunningStatus status;
    private String startedAt;
    private long startedElapsedMs;
    private long accumulatedMs;
    private long durationMs;
    private Long lastResumedElapsedMs;
    private int stepCount;
    private Long startRawSteps;
    private Long lastRawSteps;
    private int bootCount = -1;
    private boolean interrupted;
    private String createdAt;
    private String updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public RunningStatus getStatus() {
        return status;
    }

    public void setStatus(RunningStatus status) {
        this.status = status;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public long getStartedElapsedMs() {
        return startedElapsedMs;
    }

    public void setStartedElapsedMs(long startedElapsedMs) {
        this.startedElapsedMs = startedElapsedMs;
    }

    public long getAccumulatedMs() {
        return accumulatedMs;
    }

    public void setAccumulatedMs(long accumulatedMs) {
        this.accumulatedMs = accumulatedMs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getLastResumedElapsedMs() {
        return lastResumedElapsedMs;
    }

    public void setLastResumedElapsedMs(Long lastResumedElapsedMs) {
        this.lastResumedElapsedMs = lastResumedElapsedMs;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    public Long getStartRawSteps() {
        return startRawSteps;
    }

    public void setStartRawSteps(Long startRawSteps) {
        this.startRawSteps = startRawSteps;
    }

    public Long getLastRawSteps() {
        return lastRawSteps;
    }

    public void setLastRawSteps(Long lastRawSteps) {
        this.lastRawSteps = lastRawSteps;
    }

    public int getBootCount() {
        return bootCount;
    }

    public void setBootCount(int bootCount) {
        this.bootCount = bootCount;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
