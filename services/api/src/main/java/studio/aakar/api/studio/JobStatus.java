package studio.aakar.api.studio;

/** Lifecycle of a generation job. Lowercase constants: they are the contract's enum values and the DB values. */
public enum JobStatus {
    queued, running, succeeded, failed;

    public boolean terminal() {
        return this == succeeded || this == failed;
    }
}
